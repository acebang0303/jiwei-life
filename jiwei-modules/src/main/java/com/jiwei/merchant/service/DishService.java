package com.jiwei.merchant.service;

import com.jiwei.merchant.dto.DishDTO;
import com.jiwei.merchant.dto.DishPageQueryDTO;
import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.Dish;

import java.util.List;

/**
 * 菜品服务接口
 */
public interface DishService {

    /**
     * 新增菜品（含口味）
     */
    void save(DishDTO dishDTO);

    /**
     * 菜品分页查询
     */
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 根据分类查询菜品列表
     */
    List<Dish> list(Long categoryId);

    /**
     * 菜品上下架
     */
    void updateStatus(Long id, Integer status);
}
