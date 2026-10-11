package com.jiwei.merchant.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.common.result.Result;
import com.jiwei.merchant.client.AiSearchClient;
import com.jiwei.merchant.mapper.DishMapper;
import com.jiwei.merchant.mapper.MerchantInfoMapper;
import com.jiwei.merchant.service.CategoryService;
import com.jiwei.merchant.service.DishService;
import com.jiwei.merchant.service.MerchantInfoService;
import com.jiwei.merchant.service.SetmealService;
import com.jiwei.pojo.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * C端用户浏览接口
 */
@RestController
@RequestMapping("/user")
@Slf4j
@Tag(name = "用户端浏览相关接口")
public class UserShopController {

    @Autowired
    private MerchantInfoService merchantInfoService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private DishService dishService;

    @Autowired
    private SetmealService setmealService;

    @Autowired
    private AiSearchClient aiSearchClient;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    /**
     * 店铺全部菜品/套餐（按分类组织，一次加载店铺菜单）
     * 返回 {categories: [...], dishes: [...], setmeals: [...]}
     */
    @GetMapping("/merchant/{id}/dishes")
    @Operation(summary = "店铺菜单（分类+菜品+套餐）")
    public Result<Map<String, Object>> merchantDishes(@PathVariable Long id) {
        List<Category> cates = categoryService.list(id, null);
        List<Dish> dishes = new ArrayList<>();
        List<Setmeal> setmeals = new ArrayList<>();
        for (Category c : cates) {
            if (c.getType() != null && c.getType() == 2) {
                setmeals.addAll(setmealService.listByCategoryId(c.getId()));
            } else {
                dishes.addAll(dishService.list(c.getId()));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("categories", cates);
        result.put("dishes", dishes);
        result.put("setmeals", setmeals);
        return Result.success(result);
    }

    /**
     * 附近店铺列表
     * 返回所有营业中的商户
     *
     * @return
     */
    @GetMapping("/merchant/list")
    @Operation(summary = "附近店铺列表")
    public Result<List<MerchantInfo>> merchantList() {
        log.info("查询附近店铺列表");
        //1、查询所有营业中的商户
        List<MerchantInfo> list = merchantInfoService.listOpen();
        return Result.success(list);
    }

    /**
     * 店铺详情
     *
     * @param id
     * @return
     */
    @GetMapping("/merchant/{id}")
    @Operation(summary = "店铺详情")
    public Result<MerchantInfo> merchantDetail(@PathVariable Long id) {
        log.info("查询店铺详情，merchantId: {}", id);
        MerchantInfo merchant = merchantInfoService.getById(id);
        return Result.success(merchant);
    }

    /**
     * 根据店铺查分类
     *
     * @param merchantId
     * @param type
     * @return
     */
    @GetMapping("/category/list")
    @Operation(summary = "根据店铺查分类")
    public Result<List<Category>> categoryList(Long merchantId, Integer type) {
        log.info("查询分类列表，merchantId: {}, type: {}", merchantId, type);
        List<Category> list = categoryService.list(merchantId, type);
        return Result.success(list);
    }

    /**
     * 根据分类查菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/dish/list")
    @Operation(summary = "根据分类查菜品")
    public Result<List<Dish>> dishList(Long categoryId) {
        log.info("查询菜品列表，categoryId: {}", categoryId);
        List<Dish> list = dishService.list(categoryId);
        return Result.success(list);
    }

    /**
     * 根据分类查套餐
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/setmeal/list")
    @Operation(summary = "根据分类查套餐")
    public Result<List<Setmeal>> setmealList(Long categoryId) {
        log.info("查询套餐列表，categoryId: {}", categoryId);
        List<Setmeal> list = setmealService.listByCategoryId(categoryId);
        return Result.success(list);
    }

    /**
     * AI语义搜索菜品
     * 将用户自然语言查询发给 ai-service 做向量检索，并补全菜品/店铺展示字段
     *
     * @param keyword 搜索关键词（支持自然语言描述）
     * @return
     */
    @GetMapping("/dish/search")
    @Operation(summary = "AI语义搜索菜品")
    public Result<List<Map<String, Object>>> dishSearch(@RequestParam String keyword) {
        log.info("AI语义搜索菜品: {}", keyword);
        try {
            List<Map<String, Object>> raw = aiSearchClient.searchDish(keyword, 10);
            if (raw == null || raw.isEmpty()) {
                return Result.success(Collections.emptyList());
            }
            //1、收集菜品ID与店铺ID，跨库补全展示字段
            List<Long> dishIds = raw.stream()
                    .map(r -> Long.valueOf(r.get("id").toString()))
                    .collect(Collectors.toList());
            Map<Long, Dish> dishMap = dishMapper.selectBatchIds(dishIds).stream()
                    .collect(Collectors.toMap(Dish::getId, d -> d));

            Map<Long, String> merchantNameMap = new HashMap<>();
            List<Long> merchantIds = raw.stream()
                    .map(r -> r.get("merchant_id") == null ? null : Long.valueOf(r.get("merchant_id").toString()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!merchantIds.isEmpty()) {
                merchantNameMap = merchantInfoMapper.selectBatchIds(merchantIds).stream()
                        .collect(Collectors.toMap(MerchantInfo::getId, MerchantInfo::getName, (a, b) -> a));
            }

            //2、组装前端展示结构
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : raw) {
                Long dishId = Long.valueOf(r.get("id").toString());
                Dish dish = dishMap.get(dishId);
                if (dish == null) {
                    continue;
                }
                Long merchantId = r.get("merchant_id") == null ? dish.getMerchantId() : Long.valueOf(r.get("merchant_id").toString());
                String merchantName = merchantNameMap.getOrDefault(merchantId, "店铺");
                Map<String, Object> item = new HashMap<>();
                item.put("id", dishId);
                item.put("name", dish.getName());
                item.put("image", dish.getImage());
                item.put("price", dish.getPrice());
                item.put("sales", dish.getSales());
                item.put("merchantId", merchantId);
                item.put("merchantName", merchantName);
                item.put("categoryName", r.get("category_name"));
                item.put("reason", "「" + merchantName + "」的" + dish.getName() + "，与「" + keyword + "」高度相关");
                result.add(item);
            }
            return Result.success(result);
        } catch (Exception e) {
            log.error("调用AI语义搜索失败: {}", e.getMessage());
            return Result.error("AI语义搜索服务暂不可用");
        }
    }
}
