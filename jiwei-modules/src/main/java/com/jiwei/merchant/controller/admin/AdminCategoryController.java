package com.jiwei.merchant.controller.admin;

import com.jiwei.common.result.Result;
import com.jiwei.merchant.service.CategoryService;
import com.jiwei.pojo.entity.Category;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端分类接口
 */
@RestController
@RequestMapping("/admin/category")
@Slf4j
@Tag(name = "分类管理相关接口")
public class AdminCategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 新增分类
     *
     * @param category
     * @return
     */
    @PostMapping
    @Operation(summary = "新增分类")
    public Result<String> save(@RequestBody Category category) {
        log.info("新增分类: {}", category);
        categoryService.save(category);
        return Result.success();
    }

    /**
     * 查询分类列表
     *
     * @param merchantId
     * @param type
     * @return
     */
    @GetMapping("/list")
    @Operation(summary = "查询分类列表")
    public Result<List<Category>> list(Long merchantId, Integer type) {
        log.info("查询分类列表，merchantId: {}, type: {}", merchantId, type);
        List<Category> list = categoryService.list(merchantId, type);
        return Result.success(list);
    }

    /**
     * 修改分类
     *
     * @param id
     * @param category
     * @return
     */
    @PutMapping("/{id}")
    @Operation(summary = "修改分类")
    public Result<String> update(@PathVariable Long id, @RequestBody Category category) {
        category.setId(id);
        categoryService.update(category);
        return Result.success();
    }

    /**
     * 删除分类
     *
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类")
    public Result<String> delete(@PathVariable Long id) {
        categoryService.deleteById(id);
        return Result.success();
    }
}
