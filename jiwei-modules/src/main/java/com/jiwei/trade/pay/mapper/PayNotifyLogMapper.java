package com.jiwei.trade.pay.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.PayNotifyLog;
import org.apache.ibatis.annotations.Mapper;

@DS("trade")
@Mapper
public interface PayNotifyLogMapper extends BaseMapper<PayNotifyLog> {
}
