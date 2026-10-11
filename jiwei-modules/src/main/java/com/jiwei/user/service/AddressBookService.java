package com.jiwei.user.service;

import com.jiwei.pojo.entity.AddressBook;

import java.util.List;

/**
 * 收货地址服务接口
 */
public interface AddressBookService {

    /**
     * 新增收货地址
     */
    void add(AddressBook addressBook);

    /**
     * 查询用户所有地址
     */
    List<AddressBook> list(Long userId);

    /**
     * 设为默认地址
     */
    void setDefault(Long id, Long userId);

    /**
     * 修改地址
     */
    void update(AddressBook addressBook, Long userId);

    /**
     * 删除地址
     */
    void delete(Long id, Long userId);
}
