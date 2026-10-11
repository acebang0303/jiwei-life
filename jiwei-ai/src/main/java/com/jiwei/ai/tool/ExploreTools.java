package com.jiwei.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jiwei.ai.embedding.EmbeddingService;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI Agent 工具集
 * 提供给 ExploreAgent 使用的 ToolCallback
 */
@Component
@Slf4j
public class ExploreTools {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    @Qualifier("bizJdbcTemplate")
    private JdbcTemplate bizJdbcTemplate;

    @Value("${baidu.ak:}")
    private String baiduAk;

    @Value("${weather.api-key:}")
    private String weatherApiKey;

    /**
     * 语义搜索餐厅
     * 使用 pgvector 向量相似度检索
     */
    @Tool(description = "根据用户的需求（如'浪漫安静西餐厅'、'适合聚餐的火锅店'）搜索合适的餐厅，返回餐厅名称、评分、人均价格、推荐理由")
    public String semanticSearchRestaurant(
            @ToolParam(description = "用户对餐厅的需求描述，如'浪漫安静适合约会的西餐厅'") String query) {
        log.info("Tool调用 - 语义搜索餐厅: {}", query);

        //1、尝试从 pgvector 语义搜索
        try {
            List<Map<String, Object>> results = embeddingService.searchDish(query, 10);
            if (!results.isEmpty()) {
                return com.alibaba.fastjson2.JSON.toJSONString(results);
            }
        } catch (Exception e) {
            log.warn("pgvector搜索失败，使用Mock数据: {}", e.getMessage());
        }

        //2、回退：Mock 数据（开发阶段使用）
        return """
                [
                    {"name": "王品牛排", "rating": 4.8, "price": 280, "reason": "高端西餐，环境优雅适合约会，招牌台塑牛排"},
                    {"name": "海底捞火锅", "rating": 4.6, "price": 150, "reason": "服务好氛围热闹适合聚会，24小时营业"},
                    {"name": "绿茶餐厅", "rating": 4.4, "price": 80, "reason": "性价比高江浙菜系，环境清幽"},
                    {"name": "西贝莜面村", "rating": 4.5, "price": 120, "reason": "西北菜代表，适合家庭聚餐"},
                    {"name": "太二酸菜鱼", "rating": 4.7, "price": 100, "reason": "网红酸菜鱼，排队王适合年轻人"}
                ]""";
    }

    /**
     * 获取用户偏好标签
     * 从主应用 MySQL 查询 user 表的 preference_tags 字段
     */
    @Tool(description = "获取当前用户的美食偏好标签，如'火锅爱好者'、'甜品控'等")
    public String getUserPreferenceTags(@ToolParam(description = "用户ID") Long userId) {
        log.info("Tool调用 - 获取用户偏好: userId={}", userId);

        if (userId == null) {
            return "[]";
        }
        try {
            List<Map<String, Object>> rows = bizJdbcTemplate.queryForList(
                    "SELECT preference_tags FROM jiwei_user.user WHERE id = ?", userId);
            if (rows.isEmpty() || rows.get(0).get("preference_tags") == null) {
                return "[]";
            }
            return String.valueOf(rows.get(0).get("preference_tags"));
        } catch (Exception e) {
            log.warn("查询用户偏好标签失败 userId={}: {}", userId, e.getMessage());
            return "[]";
        }
    }

    /**
     * 搜索附近兴趣地点
     * 调用百度地图 POI 搜索 API
     */
    @Tool(description = "搜索指定位置附近的兴趣地点（电影院、公园、商场等）")
    public String getNearbyPOI(
            @ToolParam(description = "经度") double longitude,
            @ToolParam(description = "纬度") double latitude,
            @ToolParam(description = "地点类型，如 cinema/park/shopping") String poiType) {
        log.info("Tool调用 - 附近POI: lng={}, lat={}, type={}", longitude, latitude, poiType);

        if (baiduAk == null || baiduAk.isBlank()) {
            log.warn("百度地图 AK 未配置，无法搜索附近地点");
            return "{\"error\": \"百度地图 AK 未配置，无法搜索附近地点\"}";
        }

        String keyword = switch (poiType) {
            case "cinema" -> "电影院";
            case "park" -> "公园";
            case "shopping" -> "购物中心";
            default -> poiType;
        };
        String url = String.format(
                "https://api.map.baidu.com/place/v2/search?query=%s&location=%s,%s&radius=2000&output=json&ak=%s",
                URLUtil.encode(keyword), latitude, longitude, baiduAk);
        try {
            String resp = HttpUtil.get(url, 5000);
            JSONObject obj = JSON.parseObject(resp);
            if (obj == null || obj.getIntValue("status") != 0) {
                log.warn("百度地图返回异常: {}", resp);
                return "{\"error\": \"百度地图搜索失败\"}";
            }
            JSONArray results = obj.getJSONArray("results");
            List<String> pois = new ArrayList<>();
            if (results != null) {
                for (int i = 0; i < results.size() && i < 10; i++) {
                    JSONObject r = results.getJSONObject(i);
                    pois.add(r.getString("name") + "(" + r.getString("address") + ")");
                }
            }
            return JSON.toJSONString(pois);
        } catch (Exception e) {
            log.warn("百度地图搜索异常: {}", e.getMessage());
            return "{\"error\": \"百度地图搜索异常\"}";
        }
    }

    /**
     * 获取天气信息
     */
    @Tool(description = "获取指定城市的当前天气信息")
    public String getWeather(@ToolParam(description = "城市名称，如'杭州'") String city) {
        log.info("Tool调用 - 天气: city={}", city);

        if (weatherApiKey == null || weatherApiKey.isBlank()) {
            log.warn("天气服务未配置 API key");
            return "{\"error\": \"天气服务暂不可用（未配置 WEATHER_API_KEY）\"}";
        }
        //TODO: 接入和风天气 (QWeather) API，需要用户提供 key
        return "{\"error\": \"天气服务暂不可用\"}";
    }
}
