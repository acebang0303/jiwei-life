package com.jiwei.framework.aspect;

import com.jiwei.common.context.BaseContext;
import com.jiwei.common.enumeration.OperationType;
import com.jiwei.framework.annotation.AutoFill;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面，实现公共字段自动填充处理逻辑
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    /**
     * 切入点：mapper包下所有类的所有方法
     */
    @Pointcut("execution(* com.jiwei..mapper.*.*(..)) && @annotation(com.jiwei.framework.annotation.AutoFill)")
    public void autoFillPointCut() {}

    /**
     * 前置通知，在通知中进行公共字段的赋值
     */
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        log.info("开始进行公共字段自动填充...");

        //1、获取到当前被拦截方法上的数据库操作类型
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
        OperationType operationType = autoFill.value();

        //2、获取到当前被拦截方法的参数（实体对象）
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }

        Object entity = args[0];

        //3、准备赋值的数据
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        //4、根据当前不同的操作类型，为对应的属性通过反射赋值
        if (operationType == OperationType.INSERT) {
            try {
                Method setCreateTime = entity.getClass().getMethod("setCreateTime", LocalDateTime.class);
                Method setUpdateTime = entity.getClass().getMethod("setUpdateTime", LocalDateTime.class);
                Method setCreateUser = entity.getClass().getMethod("setCreateUser", Long.class);
                Method setUpdateUser = entity.getClass().getMethod("setUpdateUser", Long.class);

                //设置创建时间、更新时间
                setCreateTime.invoke(entity, now);
                setUpdateTime.invoke(entity, now);
                //设置创建人、更新人
                setCreateUser.invoke(entity, currentId);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                log.error("公共字段自动填充失败: {}", e.getMessage());
            }
        } else if (operationType == OperationType.UPDATE) {
            try {
                Method setUpdateTime = entity.getClass().getMethod("setUpdateTime", LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getMethod("setUpdateUser", Long.class);

                //设置更新时间、更新人
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                log.error("公共字段自动填充失败: {}", e.getMessage());
            }
        }
    }
}
