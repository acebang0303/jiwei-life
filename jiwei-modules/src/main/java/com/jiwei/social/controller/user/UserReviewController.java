package com.jiwei.social.controller.user;

import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.pojo.entity.Review;
import com.jiwei.social.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C端评价接口
 */
@RestController
@RequestMapping("/user/review")
@Slf4j
@Tag(name = "用户评价相关接口")
public class UserReviewController {

    @Autowired
    private ReviewService reviewService;

    /**
     * 发表评价
     * 提交后触发AI内容审核
     *
     * @param review
     * @return
     */
    @PostMapping
    @Operation(summary = "发表评价")
    public Result<String> publish(@RequestBody Review review) {
        log.info("发表评价，userId: {}", review.getUserId());
        reviewService.publish(review);
        return Result.success();
    }

    /**
     * 菜品评价列表（仅已审核通过）
     *
     * @param dishId
     * @return
     */
    @GetMapping("/dish/{dishId}")
    @Operation(summary = "菜品评价列表")
    public Result<List<Review>> dishReviews(@PathVariable Long dishId) {
        log.info("查询菜品评价，dishId: {}", dishId);
        List<Review> reviews = reviewService.listByDishId(dishId);
        return Result.success(reviews);
    }

    /**
     * 查询自己的评价列表
     *
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/my")
    @Operation(summary = "我的评价")
    public Result<PageResult> myReviews(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("查询我的评价，page: {}", page);
        PageResult pageResult = reviewService.myPageQuery(page, pageSize);
        return Result.success(pageResult);
    }
}
