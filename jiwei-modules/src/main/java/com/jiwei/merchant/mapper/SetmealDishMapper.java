package com.jiwei.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.SetmealDish;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;

@DS("merchant")
@Mapper
public interface SetmealDishMapper extends BaseMapper<SetmealDish> {
}
