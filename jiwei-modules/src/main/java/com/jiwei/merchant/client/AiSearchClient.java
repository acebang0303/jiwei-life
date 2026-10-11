package com.jiwei.merchant.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * AI 服务语义搜索客户端
 */
@FeignClient(name = "jiwei-ai", url = "${jiwei.ai.service-url}")
public interface AiSearchClient {

    @GetMapping("/ai/search/dish")
    List<Map<String, Object>> searchDish(@RequestParam("keyword") String keyword,
                                         @RequestParam("topK") Integer topK);
}
