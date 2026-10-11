package com.jiwei.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.Dish;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;

@DS("merchant")
@Mapper
public interface DishMapper extends BaseMapper<Dish> {
}
