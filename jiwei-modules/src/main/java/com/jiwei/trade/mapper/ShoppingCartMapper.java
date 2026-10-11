package com.jiwei.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;

/**
 * 购物车数据访问接口（MySQL兜底，主存储为Redis）
 */
@DS("trade")
@Mapper
public interface ShoppingCartMapper extends BaseMapper<ShoppingCart> {
}
