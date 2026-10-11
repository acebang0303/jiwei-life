package com.jiwei.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.merchant.mapper.CategoryMapper;
import com.jiwei.merchant.service.CategoryService;
import com.jiwei.pojo.entity.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类服务实现类
 */
@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    @CacheEvict(value = "category", allEntries = true)
    public void save(Category category) {
        categoryMapper.insert(category);
        log.info("新增分类成功，categoryId: {}", category.getId());
    }

    @Override
    @Cacheable(value = "category", key = "#merchantId + '_' + #type")
    public List<Category> list(Long merchantId, Integer type) {
        //1、构建查询条件
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(merchantId != null, Category::getMerchantId, merchantId)
               .eq(type != null, Category::getType, type)
               .orderByAsc(Category::getSort);

        //2、执行查询
        return categoryMapper.selectList(wrapper);
    }

    @Override
    @CacheEvict(value = "category", allEntries = true)
    public void update(Category category) {
        categoryMapper.updateById(category);
        log.info("修改分类成功，categoryId: {}", category.getId());
    }

    @Override
    @CacheEvict(value = "category", allEntries = true)
    public void deleteById(Long id) {
        categoryMapper.deleteById(id);
        log.info("删除分类成功，categoryId: {}", id);
    }
}
