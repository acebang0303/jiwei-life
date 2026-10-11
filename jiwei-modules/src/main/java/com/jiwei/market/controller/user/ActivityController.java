package com.jiwei.market.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.common.result.Result;
import com.jiwei.market.mapper.ActivityMapper;
import com.jiwei.pojo.entity.Activity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端首页活动接口
 */
@RestController
@RequestMapping("/user/activity")
@Slf4j
@Tag(name = "用户端活动相关接口")
public class ActivityController {

    @Autowired
    private ActivityMapper activityMapper;

    /**
     * 首页活动列表（轮播 banner）
     */
    @GetMapping("/list")
    @Operation(summary = "首页活动列表")
    public Result<List<Activity>> list() {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Activity::getStatus, 1)
               .orderByAsc(Activity::getSort);
        return Result.success(activityMapper.selectList(wrapper));
    }
}
