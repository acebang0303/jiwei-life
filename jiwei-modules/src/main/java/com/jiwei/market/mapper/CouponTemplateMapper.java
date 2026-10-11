package com.jiwei.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.CouponTemplate;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;

@DS("market")
@Mapper
public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {
}
