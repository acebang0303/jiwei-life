package com.jiwei.trade.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jiwei.common.context.BaseContext;
import com.jiwei.trade.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 购物车服务实现类
 * 购物车数据存Redis Hash，读写性能优于MySQL
 * 设置30天TTL自动清理
 */
@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final long CART_TTL = 30; //30天

    @Override
    public void add(Map<String, Object> item) {
        Long userId = BaseContext.getCurrentId();
        String cartKey = "cart:" + userId;

        //1、构建购物车条目的 field 键
        String field = buildField(item);

        //2、检查是否已存在该菜品/套餐
        String existingJson = (String) redisTemplate.opsForHash().get(cartKey, field);

        if (existingJson != null) {
            //3、已存在 → 数量+1
            JSONObject existing = JSON.parseObject(existingJson);
            existing.put("number", existing.getIntValue("number") + 1);
            redisTemplate.opsForHash().put(cartKey, field, existing.toJSONString());
        } else {
            //4、新条目
            item.put("number", item.getOrDefault("number", 1));
            redisTemplate.opsForHash().put(cartKey, field, JSON.toJSONString(item));
        }

        //5、设置过期时间
        redisTemplate.expire(cartKey, CART_TTL, TimeUnit.DAYS);
        log.info("购物车添加: userId={}, field={}", userId, field);
    }

    @Override
    public List<JSONObject> list() {
        Long userId = BaseContext.getCurrentId();
        String cartKey = "cart:" + userId;

        //1、获取购物车所有条目
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey);
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        //2、转换为 JSONObject 列表返回
        List<JSONObject> items = entries.values().stream()
                .map(v -> JSON.parseObject((String) v))
                .collect(Collectors.toList());
        return items;
    }

    @Override
    public void updateNumber(String field, Integer number) {
        Long userId = BaseContext.getCurrentId();
        String cartKey = "cart:" + userId;

        //1、获取条目JSON
        String json = (String) redisTemplate.opsForHash().get(cartKey, field);
        if (json == null) {
            log.warn("购物车条目不存在，field: {}", field);
            return;
        }

        //2、更新数量
        JSONObject item = JSON.parseObject(json);
        item.put("number", number);
        redisTemplate.opsForHash().put(cartKey, field, item.toJSONString());
    }

    @Override
    public void delete(String field) {
        Long userId = BaseContext.getCurrentId();
        String cartKey = "cart:" + userId;

        //1、从Hash中删除条目
        redisTemplate.opsForHash().delete(cartKey, field);
        log.info("购物车删除: userId={}, field={}", userId, field);
    }

    @Override
    public void clear() {
        Long userId = BaseContext.getCurrentId();
        String cartKey = "cart:" + userId;

        //1、删除整个购物车key
        redisTemplate.delete(cartKey);
        log.info("购物车清空: userId={}", userId);
    }

    /**
     * 构建Redis Hash的field键
     * 格式: {dishId}_{flavor} 或 setmeal_{setmealId}
     */
    private String buildField(Map<String, Object> item) {
        if (item.containsKey("dishId")) {
            String flavor = (String) item.getOrDefault("dishFlavor", "");
            return item.get("dishId") + (flavor.isEmpty() ? "" : "_" + flavor);
        }
        if (item.containsKey("setmealId")) {
            return "setmeal_" + item.get("setmealId");
        }
        return String.valueOf(item.hashCode());
    }
}
