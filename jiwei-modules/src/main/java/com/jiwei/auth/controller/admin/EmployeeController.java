package com.jiwei.auth.controller.admin;

import com.jiwei.auth.dto.EmployeeLoginDTO;
import com.jiwei.auth.service.EmployeeService;
import com.jiwei.common.constant.JwtClaimsConstant;
import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.framework.properties.JwtProperties;
import com.jiwei.framework.utils.JwtUtil;
import com.jiwei.pojo.entity.Employee;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 管理员认证接口
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Tag(name = "管理员认证相关接口")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * 管理员登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    @Operation(summary = "管理员登录")
    public Result<Map<String, Object>> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("管理员登录: {}", employeeLoginDTO.getUsername());

        //1、验证用户名密码
        Employee employee = employeeService.login(employeeLoginDTO);

        //2、生成JWT令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        claims.put(JwtClaimsConstant.ROLE, employee.getRoleId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        //3、构建返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("id", employee.getId());
        data.put("name", employee.getName());
        data.put("username", employee.getUsername());
        data.put("token", token);

        return Result.success(data);
    }

    /**
     * 管理员退出登录
     * 将当前token加入Redis黑名单，TTL为token剩余有效期
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    @Operation(summary = "管理员退出")
    public Result<String> logout(HttpServletRequest request) {
        //1、从请求头获取token
        String token = request.getHeader(jwtProperties.getAdminTokenName());
        if (token == null || token.isEmpty()) {
            return Result.success();
        }

        try {
            //2、解析token获取jti和剩余有效期
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            String jti = claims.getId();
            long expiration = claims.getExpiration().getTime();
            long ttl = expiration - System.currentTimeMillis();

            if (ttl > 0) {
                //3、将jti加入Redis黑名单，TTL为token剩余有效期
                redisTemplate.opsForValue().set(
                        TOKEN_BLACKLIST_PREFIX + jti,
                        "1",
                        ttl,
                        TimeUnit.MILLISECONDS
                );
                log.info("管理员退出登录，token已加入黑名单，jti: {}, ttl: {}ms", jti, ttl);
            }
        } catch (Exception e) {
            log.warn("退出登录时解析token失败: {}", e.getMessage());
        }

        return Result.success();
    }

    /**
     * 获取当前管理员信息
     */
    @GetMapping("/info")
    @Operation(summary = "获取当前管理员信息")
    public Result<Map<String, Object>> info() {
        Long empId = com.jiwei.common.context.BaseContext.getCurrentId();
        Employee employee = employeeService.getById(empId);

        Map<String, Object> data = new HashMap<>();
        data.put("id", employee.getId());
        data.put("name", employee.getName());
        data.put("username", employee.getUsername());
        data.put("roleId", employee.getRoleId());

        return Result.success(data);
    }

    /**
     * 员工分页查询
     */
    @GetMapping("/page")
    @Operation(summary = "员工分页查询")
    public Result<PageResult> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name) {
        PageResult result = employeeService.pageQuery(page, pageSize, name);
        return Result.success(result);
    }
}
