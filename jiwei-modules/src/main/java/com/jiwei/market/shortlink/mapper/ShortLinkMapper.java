package com.jiwei.market.shortlink.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.ShortLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@DS("market")
@Mapper
public interface ShortLinkMapper extends BaseMapper<ShortLink> {

    /**
     * 批量合并点击增量：一条 CASE WHEN 语句覆盖多个短码，避免逐条 UPDATE。
     * 重定向热路径不调用它，只由 ClickCountBuffer 的定时任务调用。
     */
    @Update("<script>" +
            "UPDATE short_link SET click_count = click_count + CASE short_code " +
            "<foreach collection='list' item='i'>WHEN #{i.code} THEN #{i.delta} </foreach>" +
            "END WHERE short_code IN " +
            "<foreach collection='list' item='i' open='(' separator=',' close=')'>#{i.code}</foreach>" +
            "</script>")
    int batchIncrementClickCount(@Param("list") List<ClickDelta> deltas);

    /** 单个短码的点击增量。 */
    record ClickDelta(String code, long delta) {
    }
}
