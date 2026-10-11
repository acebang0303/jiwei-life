package com.jiwei.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.AddressBook;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;

@DS("user")
@Mapper
public interface AddressBookMapper extends BaseMapper<AddressBook> {
}
