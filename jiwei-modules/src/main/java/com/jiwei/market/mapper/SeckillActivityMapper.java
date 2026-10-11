package com.jiwei.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.baomidou.dynamic.datasource.annotation.DS;

@DS("market")
@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /** DB 兜底扣减：stock >= n 条件更新防超卖 */
    @Update("UPDATE seckill_activity SET stock = stock - #{quantity}, " +
            "sold = sold + #{quantity}, version = version + 1 " +
            "WHERE id = #{activityId} AND stock >= #{quantity}")
    int deductStock(@Param("activityId") Long activityId, @Param("quantity") int quantity);

    /** 回补库存（取消/超时） */
    @Update("UPDATE seckill_activity SET stock = stock + #{quantity}, " +
            "sold = sold - #{quantity}, version = version + 1 WHERE id = #{activityId}")
    int restoreStock(@Param("activityId") Long activityId, @Param("quantity") int quantity);
}
