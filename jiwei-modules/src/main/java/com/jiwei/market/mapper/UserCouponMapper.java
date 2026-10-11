package com.jiwei.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;

@DS("market")
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
}
