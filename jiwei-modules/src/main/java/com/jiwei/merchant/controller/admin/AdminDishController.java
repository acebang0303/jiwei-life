package com.jiwei.merchant.controller.admin;

import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.merchant.dto.DishDTO;
import com.jiwei.merchant.dto.DishPageQueryDTO;
import com.jiwei.merchant.service.DishService;
import com.jiwei.pojo.entity.Dish;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端菜品接口
 */
@RestController
@RequestMapping("/admin/dish")
@Slf4j
@Tag(name = "菜品管理相关接口")
public class AdminDishController {

    @Autowired
    private DishService dishService;

    @PostMapping
    @Operation(summary = "新增菜品")
    public Result<String> save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品: {}", dishDTO);
        dishService.save(dishDTO);
        return Result.success();
    }

    @GetMapping("/page")
    @Operation(summary = "菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询: {}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/list")
    @Operation(summary = "根据分类查询菜品")
    public Result<List<Dish>> list(Long categoryId) {
        log.info("根据分类查询菜品，categoryId: {}", categoryId);
        List<Dish> list = dishService.list(categoryId);
        return Result.success(list);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "菜品上下架")
    public Result<String> updateStatus(@PathVariable Long id, Integer status) {
        log.info("菜品上下架，id: {}, status: {}", id, status);
        dishService.updateStatus(id, status);
        return Result.success();
    }
}
