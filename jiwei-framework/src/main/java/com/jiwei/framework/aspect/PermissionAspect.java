package com.jiwei.framework.aspect;

import com.jiwei.common.context.BaseContext;
import com.jiwei.framework.annotation.RequirePermission;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 权限校验切面
 * 拦截 @RequirePermission 注解的方法，查询RBAC三表校验权限
 */
@Aspect
@Component
@Slf4j
public class PermissionAspect {

    @Autowired
    private DataSource dataSource;

    @Pointcut("@annotation(com.jiwei.framework.annotation.RequirePermission)")
    public void permissionPointCut() {}

    /**
     * 校验当前用户是否拥有指定权限
     * 通过查询 RBAC 三表（employee → role → role_permission → permission）判断
     *
     * @param joinPoint 切点
     * @return 方法执行结果
     * @throws Throwable
     */
    @Around("permissionPointCut()")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        //1、获取当前方法上的 @RequirePermission 注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        String requiredPermission = requirePermission.value();

        //2、从 ThreadLocal 获取当前登录用户ID
        Long currentId = BaseContext.getCurrentId();
        if (currentId == null) {
            throw new SecurityException("用户未登录");
        }

        //3、查询RBAC三表校验权限
        if (!hasPermission(currentId, requiredPermission)) {
            log.warn("权限校验失败，用户: {}, 所需权限: {}", currentId, requiredPermission);
            throw new SecurityException("权限不足: " + requiredPermission);
        }

        log.debug("权限校验通过，用户: {}, 权限: {}", currentId, requiredPermission);

        //4、放行
        return joinPoint.proceed();
    }

    /**
     * 查询用户是否拥有指定权限
     * SELECT COUNT(*) FROM employee e
     *   JOIN role r ON e.role_id = r.id
     *   JOIN role_permission rp ON r.id = rp.role_id
     *   JOIN permission p ON rp.permission_id = p.id
     * WHERE e.id = ? AND p.code = ? AND e.status = 1 AND r.status = 1
     */
    private boolean hasPermission(Long empId, String permissionCode) {
        String sql = """
                SELECT COUNT(*) FROM employee e
                JOIN role r ON e.role_id = r.id
                JOIN role_permission rp ON r.id = rp.role_id
                JOIN permission p ON rp.permission_id = p.id
                WHERE e.id = ? AND p.code = ? AND e.status = 1 AND r.status = 1
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, empId);
            ps.setString(2, permissionCode);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            log.error("权限查询异常: {}", e.getMessage(), e);
        }

        return false;
    }
}
