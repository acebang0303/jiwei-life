package com.jiwei.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiwei.common.result.PageResult;
import com.jiwei.merchant.mapper.SetmealDishMapper;
import com.jiwei.merchant.mapper.SetmealMapper;
import com.jiwei.merchant.service.SetmealService;
import com.jiwei.pojo.entity.Setmeal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 套餐服务实现类
 */
@DS("merchant")
@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Override
    @Transactional
    @CacheEvict(value = "setmeal", allEntries = true)
    public void save(Setmeal setmeal) {
        setmealMapper.insert(setmeal);
        log.info("新增套餐成功，setmealId: {}", setmeal.getId());
    }

    @Override
    @Cacheable(value = "setmeal", key = "#page + '_' + #pageSize + '_' + #merchantId + '_' + #name")
    public PageResult pageQuery(int page, int pageSize, Long merchantId, String name) {
        //1、构建分页条件
        Page<Setmeal> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Setmeal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(merchantId != null, Setmeal::getMerchantId, merchantId)
               .like(name != null, Setmeal::getName, name)
               .orderByDesc(Setmeal::getCreateTime);

        //2、执行分页查询
        Page<Setmeal> result = setmealMapper.selectPage(p, wrapper);
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    public Setmeal getById(Long id) {
        //1、查询套餐基本信息
        return setmealMapper.selectById(id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "setmeal", allEntries = true)
    public void update(Setmeal setmeal) {
        //1、更新套餐主表
        setmealMapper.updateById(setmeal);
        log.info("修改套餐成功，setmealId: {}", setmeal.getId());
    }

    @Override
    @Transactional
    @CacheEvict(value = "setmeal", allEntries = true)
    public void deleteBatch(List<Long> ids) {
        //1、批量删除套餐
        setmealMapper.deleteBatchIds(ids);
        log.info("批量删除套餐成功，ids: {}", ids);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        //1、更新套餐状态
        Setmeal setmeal = Setmeal.builder().id(id).status(status).build();
        setmealMapper.updateById(setmeal);
        log.info("更新套餐状态，setmealId: {}, status: {}", id, status);
    }

    @Override
    public List<Setmeal> listByCategoryId(Long categoryId) {
        //1、根据分类ID查询上架中的套餐
        LambdaQueryWrapper<Setmeal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Setmeal::getCategoryId, categoryId)
               .eq(Setmeal::getStatus, 1)  // 只查询上架的套餐
               .orderByAsc(Setmeal::getCreateTime);
        return setmealMapper.selectList(wrapper);
    }
}
