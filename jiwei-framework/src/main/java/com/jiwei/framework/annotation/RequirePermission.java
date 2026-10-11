package com.jiwei.framework.annotation;

import java.lang.annotation.*;

/**
 * 权限校验注解
 * 标识需要特定权限才能访问的 Controller 方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /**
     * 权限编码，格式: module:action，如 "order:export"
     */
    String value();
}
