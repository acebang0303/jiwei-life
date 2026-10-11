package com.jiwei.user.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jiwei.auth.mapper.UserMapper;
import com.jiwei.pojo.entity.User;
import com.jiwei.user.service.GrowthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 成长值服务实现类。
 *
 * 使用 REQUIRES_NEW 开独立事务：调用方可能处于其它数据源（social/trade）的事务中，
 * 这里必须切到 user 库并独立提交，避免被外层 @DS 锁死数据源。
 */
@Service
@DS("user")
@Slf4j
public class GrowthServiceImpl implements GrowthService {

    /** 等级分档：达到该成长值即升到对应等级（1-6） */
    private static final int[] LEVEL_THRESHOLDS = {0, 100, 300, 600, 1000, 1500};

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addGrowth(Long userId, int delta) {
        if (userId == null || delta <= 0) {
            return;
        }
        //1、原子累加成长值，避免并发覆盖
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<>();
        update.eq(User::getId, userId)
              .setSql("growth_value = IFNULL(growth_value, 0) + " + delta);
        userMapper.update(null, update);

        //2、重查用户，按最新成长值计算等级并更新
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        int growth = user.getGrowthValue() != null ? user.getGrowthValue() : 0;
        int level = calcLevel(growth);
        if (!Integer.valueOf(level).equals(user.getLevel())) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getLevel, level));
            log.info("成长值等级升级 userId={}, growth={}, level={}", userId, growth, level);
        }
    }

    /**
     * 根据成长值计算等级（1-6），只升不降。
     */
    private int calcLevel(int growth) {
        int level = 1;
        for (int i = 0; i < LEVEL_THRESHOLDS.length; i++) {
            if (growth >= LEVEL_THRESHOLDS[i]) {
                level = i + 1;
            }
        }
        return level;
    }
}
