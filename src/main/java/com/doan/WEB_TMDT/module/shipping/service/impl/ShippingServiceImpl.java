package com.doan.WEB_TMDT.module.shipping.service.impl;

import com.doan.WEB_TMDT.module.shipping.dto.CalculateShippingFeeRequest;
import com.doan.WEB_TMDT.module.shipping.dto.CreateGHNOrderRequest;
import com.doan.WEB_TMDT.module.shipping.dto.CreateGHNOrderResponse;
import com.doan.WEB_TMDT.module.shipping.dto.GHNOrderDetailResponse;
import com.doan.WEB_TMDT.module.shipping.dto.ShippingFeeResponse;
import com.doan.WEB_TMDT.module.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingServiceImpl implements ShippingService {

    @Value("${ghn.api.url}")
    private String ghnApiUrl;

    @Value("${ghn.api.token}")
    private String ghnApiToken;
    
    @Value("${ghn.shop.id}")
    private Integer ghnShopId;
    
    @Value("${ghn.pick.district.id}")
    private Integer pickDistrictId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final com.doan.WEB_TMDT.module.order.repository.OrderRepository orderRepository;

    // Danh sách quận nội thành Hà Nội (miễn phí ship)
    private static final List<String> HANOI_INNER_DISTRICTS = Arrays.asList(
            "Ba Đình", "Hoàn Kiếm", "Hai Bà Trưng", "Đống Đa",
            "Tây Hồ", "Cầu Giấy", "Thanh Xuân", "Hoàng Mai",
            "Long Biên", "Nam Từ Liêm", "Bắc Từ Liêm", "Hà Đông"
    );

    @Override
    public ShippingFeeResponse calculateShippingFee(CalculateShippingFeeRequest request) {
        // 1. Check if Hanoi inner city (free ship)
        if (isHanoiInnerCity(request.getProvince(), request.getDistrict())) {
            return ShippingFeeResponse.builder()
                    .fee(0.0)
                    .shipMethod("INTERNAL")
                    .estimatedTime("1-2 ngày")
                    .isFreeShip(true)
                    .build();
        }

        // 2. Call GHN API to calculate fee and get delivery time
        Map<String, Object> ghnResult = callGHNApi(request);
        Double ghnFee = (Double) ghnResult.get("fee");
        String estimatedTime = (String) ghnResult.get("expectedTime");
        
        return ShippingFeeResponse.builder()
                .fee(ghnFee)
                .shipMethod("GHN")
                .estimatedTime(estimatedTime)
                .isFreeShip(false)
                .build();
    }

    @Override
    public boolean isHanoiInnerCity(String province, String district) {
        if (province == null || district == null) {
            return false;
        }

        String normalizedProvince = province.trim().toLowerCase();
        String normalizedDistrict = district.trim();

        boolean isHanoi = normalizedProvince.contains("hà nội") || 
                         normalizedProvince.contains("ha noi") ||
                         normalizedProvince.equals("hanoi");

        if (!isHanoi) {
            return false;
        }

        return HANOI_INNER_DISTRICTS.stream()
                .anyMatch(innerDistrict -> normalizedDistrict.contains(innerDistrict));
    }

    private Map<String, Object> callGHNApi(CalculateShippingFeeRequest request) {
        Integer toDistrictId = getDistrictId(request.getProvince(), request.getDistrict());
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Token", ghnApiToken);
        headers.set("ShopId", ghnShopId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        String expectedTime = "2-4 ngày"; // Default
        Integer serviceTypeId = 2; // Default standard service
        
        // 1. Get lead time (expected delivery time)
        try {
            String leadTimeUrl = ghnApiUrl + "/v2/shipping-order/leadtime";
            Map<String, Object> leadTimeBody = new HashMap<>();
            leadTimeBody.put("from_district_id", pickDistrictId);
            leadTimeBody.put("to_district_id", toDistrictId);
            leadTimeBody.put("service_id", serviceTypeId);
            
            HttpEntity<Map<String, Object>> leadTimeEntity = new HttpEntity<>(leadTimeBody, headers);
            
            log.info("Calling GHN Lead Time API...");
            log.info("Lead time request: {}", leadTimeBody);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> leadTimeResponse = restTemplate.postForObject(leadTimeUrl, leadTimeEntity, Map.class);
            
            log.info("Lead time response: {}", leadTimeResponse);
            
            if (leadTimeResponse != null && leadTimeResponse.get("code") != null && leadTimeResponse.get("code").equals(200)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) leadTimeResponse.get("data");
                
                if (data != null && data.containsKey("leadtime")) {
                    Long leadtimeTimestamp = ((Number) data.get("leadtime")).longValue();
                    expectedTime = formatLeadTime(leadtimeTimestamp);
                    log.info("Lead time from GHN: {} (timestamp) = {}", leadtimeTimestamp, expectedTime);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not get lead time from GHN, using default: " + e.getMessage());
        }
        
        // 2. Calculate shipping fee
        String feeUrl = ghnApiUrl + "/v2/shipping-order/fee";
        Map<String, Object> feeBody = new HashMap<>();
        feeBody.put("service_type_id", serviceTypeId);
        feeBody.put("from_district_id", pickDistrictId);
        feeBody.put("to_district_id", toDistrictId);
        feeBody.put("weight", request.getWeight() != null ? request.getWeight().intValue() : 1000);
        feeBody.put("insurance_value", request.getValue() != null ? request.getValue().intValue() : 0);
        
        log.info("=== GHN Fee API Request ===");
        log.info("URL: {}", feeUrl);
        log.info("Request body: {}", feeBody);
        
        HttpEntity<Map<String, Object>> feeEntity = new HttpEntity<>(feeBody, headers);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> feeResponse = restTemplate.postForObject(feeUrl, feeEntity, Map.class);
        
        log.info("=== GHN Fee API Response ===");
        log.info("Response: {}", feeResponse);
        
        if (feeResponse != null && feeResponse.get("code") != null && feeResponse.get("code").equals(200)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) feeResponse.get("data");
            if (data != null && data.containsKey("total")) {
                Double fee = ((Number) data.get("total")).doubleValue();
                log.info("GHN fee calculated successfully: {}", fee);
                
                Map<String, Object> result = new HashMap<>();
                result.put("fee", fee);
                result.put("expectedTime", expectedTime);
                return result;
            }
        }
        
        log.error("❌ GHN API returned unexpected response");
        throw new RuntimeException("GHN API không trả về phí vận chuyển hợp lệ");
    }
    
    private String formatLeadTime(Long leadtimeTimestamp) {
        if (leadtimeTimestamp == null || leadtimeTimestamp <= 0) {
            return "2-4 ngày";
        }
        
        try {
            // leadtime is Unix timestamp (seconds since epoch)
            // Calculate days from now to leadtime
            long currentTimestamp = System.currentTimeMillis() / 1000; // Current time in seconds
            long diffSeconds = leadtimeTimestamp - currentTimestamp;
            int days = (int) (diffSeconds / (24 * 3600));
            
            if (days <= 0) {
                return "Trong ngày";
            } else if (days == 1) {
                return "1-2 ngày";
            } else if (days == 2) {
                return "2-3 ngày";
            } else if (days == 3) {
                return "3-4 ngày";
            } else if (days >= 4 && days <= 5) {
                return "4-5 ngày";
            } else {
                return days + " ngày";
            }
        } catch (Exception e) {
            log.error("Error formatting lead time: " + e.getMessage());
            return "2-4 ngày";
        }
    }
    
    // Hardcoded district IDs from GHN for common locations
    private static final Map<String, Map<String, Integer>> DISTRICT_MAP = new HashMap<>();
    
    static {
        // Hà Nội
        Map<String, Integer> hanoi = new HashMap<>();
        hanoi.put("Ba Đình", 1454);
        hanoi.put("Hoàn Kiếm", 1452);
        hanoi.put("Hai Bà Trưng", 1451);
        hanoi.put("Đống Đa", 1450);
        hanoi.put("Tây Hồ", 1453);
        hanoi.put("Cầu Giấy", 1449);
        hanoi.put("Thanh Xuân", 1455);
        hanoi.put("Hoàng Mai", 1448);
        hanoi.put("Long Biên", 1447);
        hanoi.put("Nam Từ Liêm", 3440);
        hanoi.put("Bắc Từ Liêm", 3439);
        hanoi.put("Hà Đông", 1485);
        DISTRICT_MAP.put("Hà Nội", hanoi);
        
        // TP. Hồ Chí Minh
        Map<String, Integer> hcm = new HashMap<>();
        hcm.put("Quận 1", 1442);
        hcm.put("Quận 2", 1443);
        hcm.put("Quận 3", 1444);
        hcm.put("Quận 4", 1445);
        hcm.put("Quận 5", 1446);
        hcm.put("Quận 6", 1447);
        hcm.put("Quận 7", 1448);
        hcm.put("Quận 8", 1449);
        hcm.put("Quận 9", 1450);
        hcm.put("Quận 10", 1451);
        hcm.put("Quận 11", 1453);
        hcm.put("Quận 12", 1454);
        hcm.put("Bình Thạnh", 1462);
        hcm.put("Tân Bình", 1458);
        hcm.put("Tân Phú", 1459);
        hcm.put("Phú Nhuận", 1457);
        hcm.put("Gò Vấp", 1461);
        hcm.put("Bình Tân", 1463);
        hcm.put("Thủ Đức", 3695);
        DISTRICT_MAP.put("TP. Hồ Chí Minh", hcm);
        DISTRICT_MAP.put("Hồ Chí Minh", hcm);
        
        // Đà Nẵng
        Map<String, Integer> danang = new HashMap<>();
        danang.put("Hải Châu", 1490);
        danang.put("Thanh Khê", 1491);
        danang.put("Sơn Trà", 1492);
        danang.put("Ngũ Hành Sơn", 1493);
        danang.put("Liên Chiểu", 1494);
        danang.put("Cẩm Lệ", 1495);
        danang.put("Hòa Vang", 1496);
        DISTRICT_MAP.put("Đà Nẵng", danang);
        
        // Hải Phòng
        Map<String, Integer> haiphong = new HashMap<>();
        haiphong.put("Hồng Bàng", 1816);
        haiphong.put("Ngô Quyền", 1817);
        haiphong.put("Lê Chân", 1818);
        haiphong.put("Hải An", 1819);
        haiphong.put("Kiến An", 1820);
        haiphong.put("Đồ Sơn", 1821);
        haiphong.put("Dương Kinh", 1815);
        DISTRICT_MAP.put("Hải Phòng", haiphong);
        
        // Bình Dương
        Map<String, Integer> binhduong = new HashMap<>();
        binhduong.put("Thủ Dầu Một", 1538);
        binhduong.put("Dĩ An", 1540);
        binhduong.put("Thuận An", 1541);
        binhduong.put("Tân Uyên", 1542);
        binhduong.put("Bến Cát", 1696);
        binhduong.put("Phú Giáo", 1992);
        binhduong.put("Bàu Bàng", 3132);
        binhduong.put("Dầu Tiếng", 1746);
        binhduong.put("Bắc Tân Uyên", 3135);
        DISTRICT_MAP.put("Bình Dương", binhduong);
        
        // Đồng Nai
        Map<String, Integer> dongnai = new HashMap<>();
        dongnai.put("Biên Hòa", 1542);
        dongnai.put("Long Khánh", 1697);
        dongnai.put("Nhơn Trạch", 1698);
        dongnai.put("Trảng Bom", 1699);
        DISTRICT_MAP.put("Đồng Nai", dongnai);
    }
    
    private Integer getProvinceId(String provinceName) {
        try {
            String url = ghnApiUrl + "/master-data/province";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Token", ghnApiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            log.info("🔍 Looking for province: {}", provinceName);
            
            if (response != null && response.get("code") != null && response.get("code").equals(200)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> provinces = (List<Map<String, Object>>) response.get("data");
                
                // Normalize: remove accents, lowercase, remove special chars
                String normalizedInput = normalizeVietnamese(provinceName);
                log.info("Normalized input: {}", normalizedInput);
                
                for (Map<String, Object> province : provinces) {
                    String ghnName = (String) province.get("ProvinceName");
                    
                    if (matchLocation(ghnName, normalizedInput)) {
                        Integer provinceId = (Integer) province.get("ProvinceID");
                        log.info("Found province: {} → ID: {}", ghnName, provinceId);
                        return provinceId;
                    }
                }
                
                // Log first 5 provinces for debugging
                log.warn("❌ Province not found. Available provinces (first 5):");
                provinces.stream().limit(5).forEach(p -> 
                    log.warn("  - {}", p.get("ProvinceName"))
                );
            }
            
            log.warn("⚠️ Using default Hanoi province ID");
            return 201; // Default Hanoi province ID
            
        } catch (Exception e) {
            log.error("❌ Error getting province ID: " + e.getMessage(), e);
            return 201; // Default Hanoi
        }
    }
    
    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        
        String normalized = text.toLowerCase().trim();
        
        // Remove common prefixes but keep the rest
        normalized = normalized.replaceAll("^(tp\\.|tp |thành phố |tỉnh |quận |huyện |thị xã )", "");
        
        // Remove extra spaces only
        normalized = normalized.replaceAll("\\s+", " ").trim();
        
        return normalized;
    }
    

    private boolean matchLocation(String ghnName, String userInput) {
        String normalizedGhn = normalizeVietnamese(ghnName);
        String normalizedInput = normalizeVietnamese(userInput);
        
        // Exact match
        if (normalizedGhn.equals(normalizedInput)) {
            return true;
        }
        
        // For single digit districts (Quận 1, Quận 2, etc), require exact match with space or end
        if (normalizedInput.matches(".*\\d$")) {
            // Input ends with digit - be strict
            return normalizedGhn.equals(normalizedInput) || 
                   normalizedGhn.endsWith(" " + normalizedInput) ||
                   normalizedGhn.equals(normalizedInput + " ");
        }
        
        // General contains match
        return normalizedGhn.contains(normalizedInput) || normalizedInput.contains(normalizedGhn);
    }
    
    private Integer getDistrictId(String provinceName, String districtName) {
        log.info("🔍 Looking for district: {} in province: {}", districtName, provinceName);
        
        // Try hardcoded map first
        for (Map.Entry<String, Map<String, Integer>> provinceEntry : DISTRICT_MAP.entrySet()) {
            String mapProvinceName = provinceEntry.getKey();
            
            // Check if province matches
            if (matchLocation(mapProvinceName, provinceName)) {
                log.info("📍 Found province in map: {}", mapProvinceName);
                
                Map<String, Integer> districts = provinceEntry.getValue();
                
                // Try to find district
                for (Map.Entry<String, Integer> districtEntry : districts.entrySet()) {
                    String mapDistrictName = districtEntry.getKey();
                    
                    if (matchLocation(mapDistrictName, districtName)) {
                        Integer districtId = districtEntry.getValue();
                        log.info("Found district in map: {} → ID: {}", mapDistrictName, districtId);
                        return districtId;
                    }
                }
                
                log.warn("⚠️ District '{}' not found in map for province '{}'", districtName, mapProvinceName);
                break;
            }
        }
        
        // Fallback: Call GHN API
        log.info("🌐 District not in map, calling GHN API...");
        try {
            Integer provinceId = getProvinceId(provinceName);
            
            String url = ghnApiUrl + "/master-data/district";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("province_id", provinceId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Token", ghnApiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            if (response != null && response.get("code") != null && response.get("code").equals(200)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> districts = (List<Map<String, Object>>) response.get("data");
                
                for (Map<String, Object> district : districts) {
                    String ghnName = (String) district.get("DistrictName");
                    
                    if (matchLocation(ghnName, districtName)) {
                        Integer districtId = (Integer) district.get("DistrictID");
                        log.info("Found district via API: {} → ID: {}", ghnName, districtId);
                        return districtId;
                    }
                }
            }
            
            log.warn("⚠️ District not found via API, using default");
            return 1485;
            
        } catch (Exception e) {
            log.error("❌ Error calling GHN API: " + e.getMessage(), e);
            return 1485;
        }
    }
    
    private String getWardCode(Integer districtId, String wardName) {
        if (districtId == null || wardName == null || wardName.trim().isEmpty()) {
            log.warn("⚠️ Cannot get ward code: districtId={}, wardName={}", districtId, wardName);
            return null;
        }
        
        log.info("🔍 Looking for ward: {} in district ID: {}", wardName, districtId);
        
        try {
            String url = ghnApiUrl + "/master-data/ward";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("district_id", districtId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Token", ghnApiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            log.info("Ward API response code: {}", response != null ? response.get("code") : "null");
            
            if (response != null && response.get("code") != null && response.get("code").equals(200)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> wards = (List<Map<String, Object>>) response.get("data");
                
                if (wards != null && !wards.isEmpty()) {
                    String normalizedInput = normalizeVietnamese(wardName);
                    log.info("Normalized ward input: {}", normalizedInput);
                    
                    // First try exact match
                    for (Map<String, Object> ward : wards) {
                        String ghnName = (String) ward.get("WardName");
                        
                        if (matchLocation(ghnName, normalizedInput)) {
                            String wardCode = (String) ward.get("WardCode");
                            log.info("✅ Found ward: {} → Code: {}", ghnName, wardCode);
                            return wardCode;
                        }
                    }
                    
                    // If no match found, return the first ward as fallback
                    Map<String, Object> firstWard = wards.get(0);
                    String wardCode = (String) firstWard.get("WardCode");
                    log.warn("⚠️ Ward '{}' not found, using first ward: {} (code: {})", 
                        wardName, firstWard.get("WardName"), wardCode);
                    return wardCode;
                } else {
                    log.warn("⚠️ No wards found for district ID: {}", districtId);
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Error getting ward code: " + e.getMessage(), e);
        }
        
        return null;
    }

    @Override
    public CreateGHNOrderResponse createGHNOrder(CreateGHNOrderRequest request) {
        try {
            String url = ghnApiUrl + "/v2/shipping-order/create";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Token", ghnApiToken);
            headers.set("ShopId", ghnShopId.toString());
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Build request body
            Map<String, Object> body = new HashMap<>();
            body.put("to_name", request.getToName());
            body.put("to_phone", request.getToPhone());
            body.put("to_address", request.getToAddress());
            
            // Get ward code - required by GHN
            String wardCode = request.getToWardCode();
            if (wardCode == null || wardCode.trim().isEmpty()) {
                // Try to get ward code from district ID and address
                // For now, get the first ward of the district as fallback
                log.warn("⚠️ No ward code provided, attempting to get default ward for district: {}", request.getToDistrictId());
                wardCode = getWardCode(request.getToDistrictId(), ""); // Empty string will get first ward
            }
            
            if (wardCode != null && !wardCode.trim().isEmpty()) {
                body.put("to_ward_code", wardCode);
                log.info("✅ Using ward code: {}", wardCode);
            } else {
                log.error("❌ Cannot proceed without ward code!");
                throw new RuntimeException("Không thể tạo đơn GHN: Thiếu mã phường/xã");
            }
            
            body.put("to_district_id", request.getToDistrictId());
            body.put("note", request.getNote());
            body.put("required_note", "KHONGCHOXEMHANG"); // Required field by GHN API
            body.put("cod_amount", request.getCodAmount());
            body.put("weight", request.getWeight());
            body.put("length", request.getLength());
            body.put("width", request.getWidth());
            body.put("height", request.getHeight());
            body.put("service_type_id", request.getServiceTypeId());
            body.put("payment_type_id", request.getPaymentTypeId());
            
            // Add items
            if (request.getItems() != null && !request.getItems().isEmpty()) {
                List<Map<String, Object>> items = new ArrayList<>();
                for (CreateGHNOrderRequest.GHNOrderItem item : request.getItems()) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("name", item.getName());
                    itemMap.put("code", item.getCode());
                    itemMap.put("quantity", item.getQuantity());
                    itemMap.put("price", item.getPrice());
                    items.add(itemMap);
                }
                body.put("items", items);
            }
            
            log.info("=== GHN Create Order API Request ===");
            log.info("URL: {}", url);
            log.info("Headers: Token={}, ShopId={}", ghnApiToken.substring(0, 10) + "...", ghnShopId);
            log.info("Request body: {}", body);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            log.info("=== GHN Create Order API Response ===");
            log.info("Full Response: {}", response);
            log.info("Response Code: {}", response != null ? response.get("code") : "null");
            log.info("Response Message: {}", response != null ? response.get("message") : "null");
            
            if (response != null && response.get("code") != null && response.get("code").equals(200)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                
                if (data != null) {
                    log.info("📦 GHN Response Data Keys: {}", data.keySet());
                    log.info("📦 GHN Response Data: {}", data);
                    
                    // Parse order_code - REQUIRED field
                    String orderCode = data.get("order_code") != null ? 
                        data.get("order_code").toString() : null;
                    
                    if (orderCode == null) {
                        log.error("❌ order_code is null in GHN response!");
                        throw new RuntimeException("GHN không trả về mã đơn hàng");
                    }
                    
                    // Parse sort_code - may be null
                    String sortCode = data.get("sort_code") != null ? 
                        data.get("sort_code").toString() : null;
                    
                    // Parse total_fee - try multiple field names
                    Double totalFee = null;
                    Object feeValue = data.get("total_fee");
                    if (feeValue == null) {
                        feeValue = data.get("fee"); // Alternative field name
                    }
                    if (feeValue == null) {
                        feeValue = data.get("service_fee"); // Another alternative
                    }
                    
                    if (feeValue != null) {
                        try {
                            totalFee = ((Number) feeValue).doubleValue();
                            log.info("✅ Parsed total_fee: {}", totalFee);
                        } catch (Exception e) {
                            log.warn("⚠️ Could not parse fee value '{}': {}", feeValue, e.getMessage());
                        }
                    } else {
                        log.warn("⚠️ No fee field found in response. Available fields: {}", data.keySet());
                    }
                    
                    // Parse expected_delivery_time - can be timestamp or ISO string
                    LocalDateTime expectedDeliveryTime = null;
                    Object timeValue = data.get("expected_delivery_time");
                    
                    if (timeValue != null) {
                        try {
                            if (timeValue instanceof Number) {
                                // Unix timestamp
                                long timestamp = ((Number) timeValue).longValue();
                                expectedDeliveryTime = LocalDateTime.ofInstant(
                                    Instant.ofEpochSecond(timestamp), 
                                    ZoneId.systemDefault()
                                );
                            } else if (timeValue instanceof String) {
                                String timeStr = timeValue.toString();
                                // Try ISO format first
                                try {
                                    expectedDeliveryTime = LocalDateTime.parse(timeStr);
                                } catch (Exception e1) {
                                    // Try as timestamp string
                                    try {
                                        long timestamp = Long.parseLong(timeStr);
                                        expectedDeliveryTime = LocalDateTime.ofInstant(
                                            Instant.ofEpochSecond(timestamp), 
                                            ZoneId.systemDefault()
                                        );
                                    } catch (Exception e2) {
                                        log.warn("⚠️ Could not parse time string: {}", timeStr);
                                    }
                                }
                            }
                            log.info("✅ Parsed expected_delivery_time: {}", expectedDeliveryTime);
                        } catch (Exception e) {
                            log.warn("⚠️ Could not parse expected_delivery_time '{}': {}", timeValue, e.getMessage());
                        }
                    } else {
                        log.warn("⚠️ expected_delivery_time is null in response");
                    }
                    
                    log.info("✅ GHN order created successfully!");
                    log.info("   - Order Code: {}", orderCode);
                    log.info("   - Sort Code: {}", sortCode != null ? sortCode : "N/A");
                    log.info("   - Total Fee: {}", totalFee != null ? totalFee : "N/A");
                    log.info("   - Expected Delivery: {}", expectedDeliveryTime != null ? expectedDeliveryTime : "N/A");
                    
                    return CreateGHNOrderResponse.builder()
                            .orderCode(orderCode)
                            .status("created")
                            .expectedDeliveryTime(expectedDeliveryTime)
                            .sortCode(sortCode)
                            .totalFee(totalFee)
                            .build();
                }
            }
            
            log.error("❌ GHN API returned unexpected response");
            throw new RuntimeException("Không thể tạo đơn hàng GHN");
            
        } catch (Exception e) {
            log.error("❌ Error creating GHN order: " + e.getMessage(), e);
            throw new RuntimeException("Lỗi khi tạo đơn hàng GHN: " + e.getMessage());
        }
    }

    @Override
    public GHNOrderDetailResponse getGHNOrderDetail(String ghnOrderCode) {
        try {
            String url = ghnApiUrl + "/v2/shipping-order/detail";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Token", ghnApiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> body = new HashMap<>();
            body.put("order_code", ghnOrderCode);
            
            log.info("=== GHN Order Detail API Request ===");
            log.info("URL: {}", url);
            log.info("Order code: {}", ghnOrderCode);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            log.info("=== GHN Order Detail API Response ===");
            log.info("Response: {}", response);
            
            if (response != null && response.get("code") != null && response.get("code").equals(200)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                
                if (data != null) {
                    String status = (String) data.get("status");
                    String statusText = getStatusText(status);
                    
                    // Parse timestamps
                    LocalDateTime expectedDeliveryTime = parseTimestamp(data.get("expected_delivery_time"));
                    LocalDateTime updatedDate = parseTimestamp(data.get("updated_date"));
                    
                    // Parse logs
                    List<GHNOrderDetailResponse.StatusLog> logs = new ArrayList<>();
                    if (data.get("log") != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> logList = (List<Map<String, Object>>) data.get("log");
                        
                        for (Map<String, Object> logItem : logList) {
                            String logStatus = (String) logItem.get("status");
                            LocalDateTime logTime = parseTimestamp(logItem.get("updated_date"));
                            
                            logs.add(GHNOrderDetailResponse.StatusLog.builder()
                                    .status(logStatus)
                                    .statusText(getStatusText(logStatus))
                                    .time(logTime)
                                    .location((String) logItem.get("location"))
                                    .build());
                        }
                    }
                    
                    log.info("GHN order detail retrieved: {}", ghnOrderCode);
                    
                    return GHNOrderDetailResponse.builder()
                            .orderCode(ghnOrderCode)
                            .status(status)
                            .statusText(statusText)
                            .expectedDeliveryTime(expectedDeliveryTime)
                            .updatedDate(updatedDate)
                            .currentWarehouse((String) data.get("current_warehouse"))
                            .currentStatus(statusText)
                            .codAmount(data.get("cod_amount") != null ? 
                                ((Number) data.get("cod_amount")).doubleValue() : null)
                            .shippingFee(data.get("total_fee") != null ? 
                                ((Number) data.get("total_fee")).doubleValue() : null)
                            .note((String) data.get("note"))
                            .logs(logs)
                            .build();
                }
            }
            
            log.error("❌ GHN API returned unexpected response");
            throw new RuntimeException("Không thể lấy thông tin đơn hàng GHN");
            
        } catch (Exception e) {
            log.error("❌ Error getting GHN order detail: " + e.getMessage(), e);
            throw new RuntimeException("Lỗi khi lấy thông tin đơn hàng GHN: " + e.getMessage());
        }
    }
    
    private LocalDateTime parseTimestamp(Object timestamp) {
        if (timestamp == null) {
            return null;
        }
        
        try {
            if (timestamp instanceof Number) {
                long epochSeconds = ((Number) timestamp).longValue();
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
            } else if (timestamp instanceof String) {
                return LocalDateTime.parse((String) timestamp);
            }
        } catch (Exception e) {
            log.warn("Could not parse timestamp: {}", timestamp);
        }
        
        return null;
    }
    
    private String getStatusText(String status) {
        if (status == null) return "Không xác định";
        
        switch (status) {
            case "ready_to_pick": return "Chờ lấy hàng";
            case "picking": return "Đang lấy hàng";
            case "cancel": return "Đã hủy";
            case "money_collect_picking": return "Đang thu tiền người gửi";
            case "picked": return "Đã lấy hàng";
            case "storing": return "Hàng đang nằm ở kho";
            case "transporting": return "Đang luân chuyển";
            case "sorting": return "Đang phân loại";
            case "delivering": return "Đang giao hàng";
            case "money_collect_delivering": return "Đang thu tiền người nhận";
            case "delivered": return "Đã giao hàng";
            case "delivery_fail": return "Giao hàng thất bại";
            case "waiting_to_return": return "Chờ trả hàng";
            case "return": return "Trả hàng";
            case "return_transporting": return "Đang luân chuyển hàng trả";
            case "return_sorting": return "Đang phân loại hàng trả";
            case "returning": return "Đang trả hàng";
            case "return_fail": return "Trả hàng thất bại";
            case "returned": return "Đã trả hàng";
            case "exception": return "Đơn hàng ngoại lệ";
            case "damage": return "Hàng bị hư hỏng";
            case "lost": return "Hàng bị thất lạc";
            default: return status;
        }
    }
    
    @Override
    public List<Map<String, Object>> getProvinces() {
        try {
            String url = ghnApiUrl + "/master-data/province";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Token", ghnApiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            if (response != null && response.get("code") != null && response.get("code").equals(200)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> provinces = (List<Map<String, Object>>) response.get("data");
                
                // Transform to simpler format
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map<String, Object> province : provinces) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", province.get("ProvinceID"));
                    item.put("name", province.get("ProvinceName"));
                    result.add(item);
                }
                
                log.info("✅ Retrieved {} provinces", result.size());
                return result;
            }
            
            throw new RuntimeException("GHN API không trả về dữ liệu hợp lệ");
            
        } catch (Exception e) {
            log.error("❌ Error getting provinces: " + e.getMessage(), e);
            throw new RuntimeException("Không thể lấy danh sách tỉnh/thành phố: " + e.getMessage());
        }
    }
    
    @Override
    public List<Map<String, Object>> getDistricts(Integer provinceId) {
        try {
            String url = ghnApiUrl + "/master-data/district";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("province_id", provinceId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Token", ghnApiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            if (response != null && response.get("code") != null && response.get("code").equals(200)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> districts = (List<Map<String, Object>>) response.get("data");
                
                // Transform to simpler format
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map<String, Object> district : districts) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", district.get("DistrictID"));
                    item.put("name", district.get("DistrictName"));
                    result.add(item);
                }
                
                log.info("✅ Retrieved {} districts for province {}", result.size(), provinceId);
                return result;
            }
            
            throw new RuntimeException("GHN API không trả về dữ liệu hợp lệ");
            
        } catch (Exception e) {
            log.error("❌ Error getting districts: " + e.getMessage(), e);
            throw new RuntimeException("Không thể lấy danh sách quận/huyện: " + e.getMessage());
        }
    }
    
    @Override
    public List<Map<String, Object>> getWards(Integer districtId) {
        try {
            String url = ghnApiUrl + "/master-data/ward";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("district_id", districtId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Token", ghnApiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            if (response != null && response.get("code") != null && response.get("code").equals(200)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> wards = (List<Map<String, Object>>) response.get("data");
                
                // Transform to simpler format
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map<String, Object> ward : wards) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("code", ward.get("WardCode"));
                    item.put("name", ward.get("WardName"));
                    result.add(item);
                }
                
                log.info("✅ Retrieved {} wards for district {}", result.size(), districtId);
                return result;
            }
            
            throw new RuntimeException("GHN API không trả về dữ liệu hợp lệ");
            
        } catch (Exception e) {
            log.error("❌ Error getting wards: " + e.getMessage(), e);
            throw new RuntimeException("Không thể lấy danh sách phường/xã: " + e.getMessage());
        }
    }
    
    @Override
    public Map<String, Object> fixAllWardNames() {
        log.info("🔧 Starting to fix ward names for all orders...");
        
        int totalOrders = 0;
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        
        try {
            // Lấy tất cả orders có ward nhưng chưa có wardName
            List<com.doan.WEB_TMDT.module.order.entity.Order> orders = 
                orderRepository.findAll().stream()
                    .filter(o -> o.getWard() != null && !o.getWard().isEmpty())
                    .filter(o -> o.getWardName() == null || o.getWardName().isEmpty())
                    .toList();
            
            totalOrders = orders.size();
            log.info("📊 Found {} orders need to fix ward name", totalOrders);
            
            for (com.doan.WEB_TMDT.module.order.entity.Order order : orders) {
                try {
                    // Get district ID
                    Integer districtId = getDistrictId(order.getProvince(), order.getDistrict());
                    
                    // Get wards list
                    List<Map<String, Object>> wards = getWards(districtId);
                    
                    // Find ward by code
                    Optional<Map<String, Object>> wardOpt = wards.stream()
                        .filter(w -> order.getWard().equals(w.get("code")))
                        .findFirst();
                    
                    if (wardOpt.isPresent()) {
                        String wardName = (String) wardOpt.get().get("name");
                        order.setWardName(wardName);
                        
                        // Rebuild shippingAddress with correct ward name
                        String newShippingAddress = String.format("%s, %s, %s, %s",
                            order.getAddress(), wardName, 
                            order.getDistrict(), order.getProvince());
                        order.setShippingAddress(newShippingAddress);
                        
                        orderRepository.save(order);
                        
                        successCount++;
                        log.info("✅ Updated order {} with ward name: {} and rebuilt address", 
                            order.getOrderCode(), wardName);
                    } else {
                        failCount++;
                        String error = "Order " + order.getOrderCode() + ": Ward code " + order.getWard() + " not found in district " + districtId;
                        errors.add(error);
                        log.warn("⚠️ {}", error);
                    }
                    
                } catch (Exception e) {
                    failCount++;
                    String error = "Order " + order.getOrderCode() + ": " + e.getMessage();
                    errors.add(error);
                    log.error("❌ Error fixing order {}: {}", order.getOrderCode(), e.getMessage());
                }
            }
            
            log.info("🎉 Fix ward names completed!");
            log.info("   Total: {}", totalOrders);
            log.info("   Success: {}", successCount);
            log.info("   Failed: {}", failCount);
            
            Map<String, Object> result = new HashMap<>();
            result.put("total", totalOrders);
            result.put("success", successCount);
            result.put("failed", failCount);
            result.put("errors", errors);
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Fatal error fixing ward names: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi khi cập nhật tên phường/xã: " + e.getMessage());
        }
    }
}
