package com.jiwei.merchant.service;

import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.Setmeal;
import com.jiwei.pojo.entity.SetmealDish;

import java.util.List;

/**
 * 套餐服务接口
 */
public interface SetmealService {

    /**
     * 新增套餐
     */
    void save(Setmeal setmeal);

    /**
     * 分页查询套餐
     */
    PageResult pageQuery(int page, int pageSize, Long merchantId, String name);

    /**
     * 查询套餐详情
     */
    Setmeal getById(Long id);

    /**
     * 修改套餐
     */
    void update(Setmeal setmeal);

    /**
     * 批量删除套餐
     */
    void deleteBatch(List<Long> ids);

    /**
     * 套餐上下架
     */
    void updateStatus(Long id, Integer status);

    /**
     * 根据分类查询套餐列表
     * 供C端用户浏览使用
     */
    List<Setmeal> listByCategoryId(Long categoryId);
}
