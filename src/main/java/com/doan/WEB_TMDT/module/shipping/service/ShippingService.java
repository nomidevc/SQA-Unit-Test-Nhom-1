package com.doan.WEB_TMDT.module.shipping.service;

import com.doan.WEB_TMDT.module.shipping.dto.CalculateShippingFeeRequest;
import com.doan.WEB_TMDT.module.shipping.dto.CreateGHNOrderRequest;
import com.doan.WEB_TMDT.module.shipping.dto.CreateGHNOrderResponse;
import com.doan.WEB_TMDT.module.shipping.dto.GHNOrderDetailResponse;
import com.doan.WEB_TMDT.module.shipping.dto.ShippingFeeResponse;

import java.util.List;
import java.util.Map;

public interface ShippingService {
    ShippingFeeResponse calculateShippingFee(CalculateShippingFeeRequest request);
    boolean isHanoiInnerCity(String province, String district);
    CreateGHNOrderResponse createGHNOrder(CreateGHNOrderRequest request);
    GHNOrderDetailResponse getGHNOrderDetail(String ghnOrderCode);
    
    // Address APIs
    List<Map<String, Object>> getProvinces();
    List<Map<String, Object>> getDistricts(Integer provinceId);
    List<Map<String, Object>> getWards(Integer districtId);
    
    // Fix ward names for old orders
    Map<String, Object> fixAllWardNames();
}
