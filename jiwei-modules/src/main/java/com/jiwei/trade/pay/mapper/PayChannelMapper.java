package com.jiwei.trade.pay.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.PayChannel;
import org.apache.ibatis.annotations.Mapper;

@DS("trade")
@Mapper
public interface PayChannelMapper extends BaseMapper<PayChannel> {
}
