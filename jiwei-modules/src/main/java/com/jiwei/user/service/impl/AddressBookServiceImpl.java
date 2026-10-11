package com.jiwei.user.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jiwei.common.context.BaseContext;
import com.jiwei.user.mapper.AddressBookMapper;
import com.jiwei.user.service.AddressBookService;
import com.jiwei.pojo.entity.AddressBook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@DS("user")
@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Override
    public void add(AddressBook addressBook) {
        //1、设置用户ID
        addressBook.setUserId(BaseContext.getCurrentId());

        //2、如果是第一个地址，自动设为默认
        Long count = addressBookMapper.selectCount(
                new LambdaQueryWrapper<AddressBook>().eq(AddressBook::getUserId, addressBook.getUserId()));
        if (count == 0) {
            addressBook.setIsDefault(1);
        }

        //3、新增地址
        addressBookMapper.insert(addressBook);
        log.info("新增收货地址成功，userId: {}", addressBook.getUserId());
    }

    @Override
    public List<AddressBook> list(Long userId) {
        LambdaQueryWrapper<AddressBook> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AddressBook::getUserId, userId)
               .orderByDesc(AddressBook::getIsDefault)
               .orderByDesc(AddressBook::getUpdateTime);
        return addressBookMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public void setDefault(Long id, Long userId) {
        //1、清除旧的默认地址
        LambdaUpdateWrapper<AddressBook> clearWrapper = new LambdaUpdateWrapper<>();
        clearWrapper.eq(AddressBook::getUserId, userId)
                     .set(AddressBook::getIsDefault, 0);
        addressBookMapper.update(null, clearWrapper);

        //2、设置新的默认地址（校验归属，防止把他人地址设为自己的默认）
        LambdaUpdateWrapper<AddressBook> setWrapper = new LambdaUpdateWrapper<>();
        setWrapper.eq(AddressBook::getId, id)
                  .eq(AddressBook::getUserId, userId)
                  .set(AddressBook::getIsDefault, 1);
        addressBookMapper.update(null, setWrapper);

        log.info("设置默认地址成功，addressId: {}, userId: {}", id, userId);
    }

    @Override
    public void update(AddressBook addressBook, Long userId) {
        //归属校验：只能改自己的地址
        addressBook.setUserId(userId);
        int updated = addressBookMapper.update(addressBook,
                new LambdaQueryWrapper<AddressBook>()
                        .eq(AddressBook::getId, addressBook.getId())
                        .eq(AddressBook::getUserId, userId));
        if (updated == 0) {
            throw new com.jiwei.common.exception.BaseException("地址不存在或无权修改");
        }
    }

    @Override
    public void delete(Long id, Long userId) {
        int deleted = addressBookMapper.delete(
                new LambdaQueryWrapper<AddressBook>()
                        .eq(AddressBook::getId, id)
                        .eq(AddressBook::getUserId, userId));
        if (deleted == 0) {
            throw new com.jiwei.common.exception.BaseException("地址不存在或无权删除");
        }
    }
}
