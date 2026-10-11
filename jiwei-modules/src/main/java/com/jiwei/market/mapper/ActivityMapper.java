package com.jiwei.market.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.Activity;
import org.apache.ibatis.annotations.Mapper;

@DS("market")
@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
}
