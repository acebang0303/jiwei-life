package com.jiwei.merchant.service;

import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.Category;

import java.util.List;

/**
 * 分类服务接口
 */
public interface CategoryService {

    /**
     * 新增分类
     */
    void save(Category category);

    /**
     * 根据商户和类型查询分类列表
     */
    List<Category> list(Long merchantId, Integer type);

    /**
     * 修改分类
     */
    void update(Category category);

    /**
     * 删除分类
     */
    void deleteById(Long id);
}
