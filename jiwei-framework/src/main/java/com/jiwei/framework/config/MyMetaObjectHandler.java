package com.jiwei.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.jiwei.common.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 元对象处理器
 * 用于自动填充 create_time, update_time, create_user, update_user 字段
 */
@Component
@Slf4j
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.info("开始自动填充插入字段...");

        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        //填充创建时间
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        //填充更新时间
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        //填充创建人
        this.strictInsertFill(metaObject, "createUser", Long.class, currentId);
        //填充更新人
        this.strictInsertFill(metaObject, "updateUser", Long.class, currentId);
    }

    /**
     * 更新时自动填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("开始自动填充更新字段...");

        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        //填充更新时间
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        //填充更新人
        this.strictUpdateFill(metaObject, "updateUser", Long.class, currentId);
    }
}
