package com.jiwei.user.controller;

import com.jiwei.common.result.Result;
import com.jiwei.pojo.entity.AddressBook;
import com.jiwei.user.service.AddressBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C端收货地址接口
 */
@RestController
@RequestMapping("/user/address")
@Slf4j
@Tag(name = "收货地址相关接口")
public class AddressBookController {

    @Autowired
    private AddressBookService addressBookService;

    /**
     * 新增收货地址
     */
    @PostMapping
    @Operation(summary = "新增收货地址")
    public Result<String> add(@RequestBody AddressBook addressBook) {
        log.info("新增收货地址: {}", addressBook);
        addressBookService.add(addressBook);
        return Result.success();
    }

    /**
     * 查询收货地址列表
     */
    @GetMapping
    @Operation(summary = "查询收货地址列表")
    public Result<List<AddressBook>> list() {
        log.info("查询收货地址列表");
        List<AddressBook> list = addressBookService.list(
                com.jiwei.common.context.BaseContext.getCurrentId());
        return Result.success(list);
    }

    /**
     * 设为默认地址
     */
    @PutMapping("/{id}/default")
    @Operation(summary = "设为默认地址")
    public Result<String> setDefault(@PathVariable Long id) {
        log.info("设为默认地址，id: {}", id);
        addressBookService.setDefault(id, com.jiwei.common.context.BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * 修改收货地址
     */
    @PutMapping("/{id}")
    @Operation(summary = "修改收货地址")
    public Result<String> update(@PathVariable Long id, @RequestBody AddressBook addressBook) {
        log.info("修改收货地址，id: {}", id);
        addressBook.setId(id);
        addressBookService.update(addressBook, com.jiwei.common.context.BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * 删除收货地址
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除收货地址")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除收货地址，id: {}", id);
        addressBookService.delete(id, com.jiwei.common.context.BaseContext.getCurrentId());
        return Result.success();
    }
}
