package com.jiwei.framework.annotation;

import com.jiwei.common.enumeration.OperationType;
import java.lang.annotation.*;

/**
 * 公共字段自动填充注解
 * 标识需要自动填充 createTime/updateTime/createUser/updateUser 的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {
    /**
     * 数据库操作类型
     */
    OperationType value();
}
