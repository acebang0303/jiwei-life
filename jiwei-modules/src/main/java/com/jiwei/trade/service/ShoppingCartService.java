package com.jiwei.trade.service;

import java.util.List;
import java.util.Map;

/**
 * 购物车服务接口
 * 主存储: Redis Hash  key=cart:{userId} field={dishId}_{flavor} 或 {setmealId}
 * MySQL ShoppingCart表仅做持久化兜底
 */
public interface ShoppingCartService {

    /**
     * 添加购物车
     * @param item Map包含: dishId/setmealId, name, image, amount, dishFlavor, number
     */
    void add(Map<String, Object> item);

    /**
     * 查看购物车
     * @return 购物车条目列表（JSONObject）
     */
    List<?> list();

    /**
     * 修改购物车数量
     */
    void updateNumber(String field, Integer number);

    /**
     * 删除购物车条目
     */
    void delete(String field);

    /**
     * 清空购物车
     */
    void clear();
}
