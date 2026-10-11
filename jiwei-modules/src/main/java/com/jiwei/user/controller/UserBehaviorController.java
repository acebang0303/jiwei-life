package com.jiwei.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.common.context.BaseContext;
import com.jiwei.common.result.Result;
import com.jiwei.pojo.entity.UserBehavior;
import com.jiwei.user.mapper.UserBehaviorMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * C端用户行为上报接口（驱动 AI 个性化推荐）
 */
@RestController
@RequestMapping("/user/behavior")
@Slf4j
@Tag(name = "用户行为上报接口")
public class UserBehaviorController {

    @Autowired
    private UserBehaviorMapper userBehaviorMapper;

    /**
     * 上报行为：LIKE_NOTE / COLLECT_NOTE / COMMENT_NOTE / VIEW_MERCHANT
     * 同类型同目标去重（重复上报只记一次）
     */
    @PostMapping
    @Operation(summary = "上报用户行为")
    public Result<String> report(@RequestBody Map<String, Object> body) {
        Long userId = BaseContext.getCurrentId();
        String type = String.valueOf(body.get("type"));
        Long targetId = body.get("targetId") == null
                ? null : Long.valueOf(body.get("targetId").toString());
        if (type == null || targetId == null) {
            return Result.error("参数不完整");
        }
        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBehavior::getUserId, userId)
               .eq(UserBehavior::getType, type)
               .eq(UserBehavior::getTargetId, targetId);
        Long exist = userBehaviorMapper.selectCount(wrapper);
        if (exist == null || exist == 0) {
            userBehaviorMapper.insert(UserBehavior.builder()
                    .userId(userId).type(type).targetId(targetId).build());
            log.info("行为上报: userId={}, type={}, targetId={}", userId, type, targetId);
        }
        return Result.success();
    }

    /**
     * 我的行为摘要（开发调试用）
     */
    @GetMapping("/my")
    @Operation(summary = "我的行为列表")
    public Result<List<UserBehavior>> my() {
        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBehavior::getUserId, BaseContext.getCurrentId())
               .orderByDesc(UserBehavior::getCreateTime)
               .last("LIMIT 100");
        return Result.success(userBehaviorMapper.selectList(wrapper));
    }
}
