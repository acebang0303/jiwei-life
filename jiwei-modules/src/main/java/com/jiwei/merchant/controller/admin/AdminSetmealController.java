package com.jiwei.merchant.controller.admin;

import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.merchant.service.SetmealService;
import com.jiwei.pojo.entity.Setmeal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端套餐接口
 */
@RestController
@RequestMapping("/admin/setmeal")
@Slf4j
@Tag(name = "套餐管理相关接口")
public class AdminSetmealController {

    @Autowired
    private SetmealService setmealService;

    /**
     * 新增套餐
     *
     * @param setmeal
     * @return
     */
    @PostMapping
    @Operation(summary = "新增套餐")
    public Result<String> save(@RequestBody Setmeal setmeal) {
        log.info("新增套餐: {}", setmeal);
        setmealService.save(setmeal);
        return Result.success();
    }

    /**
     * 分页查询套餐
     *
     * @param page
     * @param pageSize
     * @param merchantId
     * @param name
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询套餐")
    public Result<PageResult> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            Long merchantId, String name) {
        log.info("分页查询套餐，page: {}, pageSize: {}", page, pageSize);
        PageResult pageResult = setmealService.pageQuery(page, pageSize, merchantId, name);
        return Result.success(pageResult);
    }

    /**
     * 查询套餐详情
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询套餐详情")
    public Result<Setmeal> getById(@PathVariable Long id) {
        Setmeal setmeal = setmealService.getById(id);
        return Result.success(setmeal);
    }

    /**
     * 修改套餐
     *
     * @param id
     * @param setmeal
     * @return
     */
    @PutMapping("/{id}")
    @Operation(summary = "修改套餐")
    public Result<String> update(@PathVariable Long id, @RequestBody Setmeal setmeal) {
        setmeal.setId(id);
        setmealService.update(setmeal);
        return Result.success();
    }

    /**
     * 批量删除套餐
     *
     * @param ids
     * @return
     */
    @DeleteMapping
    @Operation(summary = "批量删除套餐")
    public Result<String> delete(@RequestParam List<Long> ids) {
        setmealService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 套餐上下架
     *
     * @param id
     * @param status
     * @return
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "套餐上下架")
    public Result<String> updateStatus(@PathVariable Long id, Integer status) {
        setmealService.updateStatus(id, status);
        return Result.success();
    }
}
