package com.doan.WEB_TMDT.module.shipping.client;

import com.doan.WEB_TMDT.module.shipping.constants.ShippingConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Client để gọi API của Giao Hàng Nhanh (GHN)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GHNApiClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${ghn.api.url:https://dev-online-gateway.ghn.vn/shiip/public-api}")
    private String ghnApiUrl;
    
    @Value("${ghn.api.token}")
    private String ghnToken;
    
    @Value("${ghn.shop.id}")
    private Integer ghnShopId;
    

    public Map<String, Object> calculateShippingFee(
            Integer toDistrictId,
            String toWardCode,
            Integer weight,
            Integer length,
            Integer width,
            Integer height,
            Integer insuranceValue
    ) {
        try {
            // 1. Get service type
            Integer serviceTypeId = getAvailableServiceType(toDistrictId);
            
            // 2. Calculate fee
            String feeUrl = ghnApiUrl + "/v2/shipping-order/fee";
            
            Map<String, Object> feeBody = new HashMap<>();
            feeBody.put("service_type_id", serviceTypeId);
            feeBody.put("from_district_id", ShippingConstants.SHOP_DISTRICT_ID);
            feeBody.put("to_district_id", toDistrictId);
            feeBody.put("to_ward_code", toWardCode);
            feeBody.put("weight", weight);
            feeBody.put("length", length);
            feeBody.put("width", width);
            feeBody.put("height", height);
            feeBody.put("insurance_value", insuranceValue);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> feeRequest = new HttpEntity<>(feeBody, headers);
            
            ResponseEntity<Map> feeResponse = restTemplate.exchange(
                    feeUrl, HttpMethod.POST, feeRequest, Map.class);
            
            Map<String, Object> feeData = (Map<String, Object>) feeResponse.getBody().get("data");
            Double totalFee = ((Number) feeData.get("total")).doubleValue();
            
            // 3. Get expected delivery time
            String timeUrl = ghnApiUrl + "/v2/shipping-order/leadtime";
            
            Map<String, Object> timeBody = new HashMap<>();
            timeBody.put("from_district_id", ShippingConstants.SHOP_DISTRICT_ID);
            timeBody.put("from_ward_code", ShippingConstants.SHOP_WARD_CODE);
            timeBody.put("to_district_id", toDistrictId);
            timeBody.put("to_ward_code", toWardCode);
            timeBody.put("service_id", serviceTypeId);
            
            HttpEntity<Map<String, Object>> timeRequest = new HttpEntity<>(timeBody, headers);
            
            ResponseEntity<Map> timeResponse = restTemplate.exchange(
                    timeUrl, HttpMethod.POST, timeRequest, Map.class);
            
            Map<String, Object> timeData = (Map<String, Object>) timeResponse.getBody().get("data");
            String expectedTime = (String) timeData.get("leadtime");
            
            Map<String, Object> result = new HashMap<>();
            result.put("fee", totalFee);
            result.put("expectedTime", expectedTime);
            result.put("serviceTypeId", serviceTypeId);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error calculating GHN shipping fee", e);
            throw new RuntimeException("Không thể tính phí ship: " + e.getMessage());
        }
    }
    

    private Integer getAvailableServiceType(Integer toDistrictId) {
        try {
            String url = ghnApiUrl + "/v2/shipping-order/available-services";
            
            Map<String, Object> body = new HashMap<>();
            body.put("shop_id", ghnShopId);
            body.put("from_district", ShippingConstants.SHOP_DISTRICT_ID);
            body.put("to_district", toDistrictId);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);
            
            List<Map<String, Object>> services = (List<Map<String, Object>>) response.getBody().get("data");
            
            if (services != null && !services.isEmpty()) {
                return ((Number) services.get(0).get("service_type_id")).intValue();
            }
            
            return ShippingConstants.GHN_SERVICE_TYPE_STANDARD;
            
        } catch (Exception e) {
            log.warn("Error getting GHN service type, using default", e);
            return ShippingConstants.GHN_SERVICE_TYPE_STANDARD;
        }
    }
    
    /**
     * Lấy District ID từ tên tỉnh và quận
     */
    public Integer getDistrictId(String provinceName, String districtName) {
        try {
            // 1. Get province ID
            String provinceUrl = ghnApiUrl + "/master-data/province";
            HttpHeaders headers = createHeaders();
            HttpEntity<?> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> provinceResponse = restTemplate.exchange(
                    provinceUrl, HttpMethod.GET, request, Map.class);
            
            List<Map<String, Object>> provinces = (List<Map<String, Object>>) provinceResponse.getBody().get("data");
            
            Integer provinceId = null;
            for (Map<String, Object> province : provinces) {
                String name = (String) province.get("ProvinceName");
                if (name.toLowerCase().contains(provinceName.toLowerCase())) {
                    provinceId = ((Number) province.get("ProvinceID")).intValue();
                    break;
                }
            }
            
            if (provinceId == null) {
                throw new RuntimeException("Không tìm thấy tỉnh: " + provinceName);
            }
            
            // 2. Get district ID
            String districtUrl = ghnApiUrl + "/master-data/district?province_id=" + provinceId;
            
            ResponseEntity<Map> districtResponse = restTemplate.exchange(
                    districtUrl, HttpMethod.GET, request, Map.class);
            
            List<Map<String, Object>> districts = (List<Map<String, Object>>) districtResponse.getBody().get("data");
            
            for (Map<String, Object> district : districts) {
                String name = (String) district.get("DistrictName");
                if (name.toLowerCase().contains(districtName.toLowerCase())) {
                    return ((Number) district.get("DistrictID")).intValue();
                }
            }
            
            throw new RuntimeException("Không tìm thấy quận/huyện: " + districtName);
            
        } catch (Exception e) {
            log.error("Error getting district ID", e);
            throw new RuntimeException("Không thể lấy thông tin địa chỉ: " + e.getMessage());
        }
    }
    
    /**
     * Tạo HTTP headers cho GHN API
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Token", ghnToken);
        headers.set("ShopId", String.valueOf(ghnShopId));
        return headers;
    }
}
