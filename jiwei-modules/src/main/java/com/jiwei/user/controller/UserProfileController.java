package com.jiwei.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.common.context.BaseContext;
import com.jiwei.common.result.Result;
import com.jiwei.pojo.entity.Follow;
import com.jiwei.pojo.entity.Orders;
import com.jiwei.pojo.entity.User;
import com.jiwei.auth.service.UserAuthService;
import com.jiwei.social.mapper.FollowMapper;
import com.jiwei.trade.mapper.OrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * C端用户个人信息接口
 */
@RestController
@RequestMapping("/user/profile")
@Slf4j
@Tag(name = "用户个人信息相关接口")
public class UserProfileController {

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 个人主页统计数据（订单/收藏/关注/粉丝）
     */
    @GetMapping("/stats")
    @Operation(summary = "个人主页统计")
    public Result<Map<String, Object>> stats() {
        Long userId = BaseContext.getCurrentId();
        Map<String, Object> result = new HashMap<>();
        //订单数
        result.put("orderCount", orderMapper.selectCount(
                new LambdaQueryWrapper<Orders>().eq(Orders::getUserId, userId)));
        //收藏数（Redis 用户收藏集合）
        Long collectCount = redisTemplate.opsForSet().size("user:collect:" + userId);
        result.put("collectCount", collectCount != null ? collectCount : 0);
        //关注数
        result.put("followCount", followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId)));
        //粉丝数
        result.put("fansCount", followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, userId)));
        return Result.success(result);
    }

    /**
     * 获取个人信息
     *
     * @return
     */
    @GetMapping
    @Operation(summary = "获取个人信息")
    public Result<User> getProfile() {
        Long userId = BaseContext.getCurrentId();
        User user = userAuthService.getUserById(userId);
        // 脱敏: 清除 openid
        if (user != null) {
            user.setOpenid(null);
        }
        return Result.success(user);
    }

    /**
     * 修改个人信息
     *
     * @param user
     * @return
     */
    @PutMapping
    @Operation(summary = "修改个人信息")
    public Result<String> updateProfile(@RequestBody User user) {
        Long userId = BaseContext.getCurrentId();
        user.setId(userId);
        user.setOpenid(null); //不更新openid
        userAuthService.updateUser(user);
        return Result.success();
    }

    /**
     * 查看成长值详情
     *
     * @return
     */
    @GetMapping("/growth")
    @Operation(summary = "查看成长值详情")
    public Result<Map<String, Object>> growth() {
        Long userId = BaseContext.getCurrentId();
        User user = userAuthService.getUserById(userId);
        Map<String, Object> result = new HashMap<>();
        if (user != null) {
            result.put("growthValue", user.getGrowthValue());
            result.put("level", user.getLevel());
        }
        return Result.success(result);
    }
}
