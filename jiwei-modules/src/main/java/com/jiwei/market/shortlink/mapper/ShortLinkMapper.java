package com.jiwei.market.shortlink.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.ShortLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@DS("market")
@Mapper
public interface ShortLinkMapper extends BaseMapper<ShortLink> {

    @Update("UPDATE short_link SET click_count = click_count + 1 WHERE short_code = #{code}")
    int incrementClickCount(@Param("code") String code);
}
