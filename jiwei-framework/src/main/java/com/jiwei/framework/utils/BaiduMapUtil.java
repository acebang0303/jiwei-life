package com.jiwei.framework.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jiwei.framework.properties.BaiduProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 百度地图工具类
 * 用于配送范围校验：将地址转为经纬度 → 计算直线距离 → 判断是否超出配送范围
 *
 * 需手动填写的配置（application-dev.yml）：
 *   jiwei.baidu.ak = 你的百度地图AK
 *
 * AK 获取方式：https://lbsyun.baidu.com/apiconsole/key
 * 需要开通的API：Geocoding API（地址→经纬度）、Direction API（路线规划）
 */
@Component
@Slf4j
public class BaiduMapUtil {

    @Autowired
    private BaiduProperties baiduProperties;

    private static final String GEOCODING_URL = "https://api.map.baidu.com/geocoding/v3";
    private static final String ROUTE_URL = "https://api.map.baidu.com/directionlite/v1/driving";

    /** 默认配送范围（米） */
    private static final int DEFAULT_DELIVERY_RANGE = 5000;

    /** 地球半径（米），用于Haversine公式计算直线距离 */
    private static final double EARTH_RADIUS = 6371000;

    /**
     * 检查用户收货地址是否在配送范围内
     *
     * @param shopAddress 店铺地址（如"杭州市西湖区文三路18号"）
     * @param userAddress 用户收货地址（如"杭州市拱墅区湖墅南路22号"）
     * @param deliveryRange 配送范围（米），null则使用默认5000米
     * @return true=在配送范围内，false=超出配送范围
     */
    public boolean checkDeliveryRange(String shopAddress, String userAddress, Integer deliveryRange) {
        // ========== 你需要手动填写 baiduProperties.getAk() ==========
        // 在 application-dev.yml 或环境变量中配置：BAIDU_AK

        int range = deliveryRange != null ? deliveryRange : DEFAULT_DELIVERY_RANGE;

        //1、店铺地址 → 经纬度
        double[] shopLocation = getLocation(shopAddress);
        if (shopLocation == null) {
            log.warn("店铺地址解析失败，默认放行（Mock模式）: {}", shopAddress);
            // Mock模式下店铺地址解析失败不限制配送范围
            return true;
        }

        //2、用户地址 → 经纬度
        double[] userLocation = getLocation(userAddress);
        if (userLocation == null) {
            log.warn("用户地址解析失败，默认放行（Mock模式）: {}", userAddress);
            return true;
        }

        //3、计算直线距离（Haversine公式）
        double distance = haversineDistance(
                shopLocation[0], shopLocation[1],
                userLocation[0], userLocation[1]);

        log.info("配送距离计算: shop=({},{}), user=({},{}), distance={}米, 配送范围={}米",
                shopLocation[0], shopLocation[1],
                userLocation[0], userLocation[1],
                Math.round(distance), range);

        //4、判断是否在配送范围内
        return distance <= range;
    }

    /**
     * 通过百度地图 Geocoding API 将地址转为经纬度
     *
     * 文档: https://lbsyun.baidu.com/faq/api?title=webapi/guide/webservice-geocoding
     *
     * @param address 中文地址
     * @return [经度(lng), 纬度(lat)]，失败返回 null
     */
    private double[] getLocation(String address) {
        if (StrUtil.isBlank(address)) {
            return null;
        }

        String ak = baiduProperties.getAk();
        if (StrUtil.isBlank(ak) || "EFEEFFEFEFE".equals(ak)) {
            // ========== Mock 模式：没有配置真正的 AK ==========
            // 请到 https://lbsyun.baidu.com/apiconsole/key 申请AK后填入配置
            // 此 Mock 返回杭州西湖区中心坐标，真实场景不准确
            log.debug("百度地图AK未配置，使用Mock坐标（杭州）");
            return new double[]{120.15, 30.28}; // Mock: 杭州中心
        }

        try {
            //1、调用百度地图 Geocoding API
            String url = StrUtil.format("{}?address={}&output=json&ak={}",
                    GEOCODING_URL, address, ak);
            String response = HttpUtil.get(url);
            JSONObject result = JSONUtil.parseObj(response);

            //2、解析返回结果
            int status = result.getInt("status");
            if (status == 0) {
                JSONObject location = result.getByPath("result.location", JSONObject.class);
                if (location != null) {
                    double lng = location.getDouble("lng");
                    double lat = location.getDouble("lat");
                    log.debug("地址解析成功: {} → (lng={}, lat={})", address, lng, lat);
                    return new double[]{lng, lat};
                }
            }

            log.warn("百度地图地址解析失败: status={}, address={}, response={}", status, address, response);
        } catch (Exception e) {
            log.error("百度地图API调用异常: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Haversine 公式计算两点间直线距离（米）
     */
    private double haversineDistance(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }
}
