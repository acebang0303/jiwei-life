package com.jiwei.framework.handler;

import com.jiwei.common.constant.MessageConstant;
import com.jiwei.common.exception.BaseException;
import com.jiwei.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.file.AccessDeniedException;
import java.sql.SQLException;

/**
 * 全局异常处理器
 * 统一处理各层抛出的异常，返回标准 Result 响应
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(BaseException.class)
    public Result<?> handleBaseException(BaseException ex) {
        log.error("业务异常: {}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 处理参数校验失败异常
     * 如: @Valid 校验、@NotNull/@NotBlank 等注解校验
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        //1、收集所有字段校验错误信息
        StringBuilder sb = new StringBuilder();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                sb.append(error.getField()).append(": ").append(error.getDefaultMessage()).append("; "));
        String message = sb.length() > 0 ? sb.toString() : "参数校验失败";
        log.warn("参数校验失败: {}", message);
        return Result.error("参数校验失败: " + message);
    }

    /**
     * 处理权限不足异常
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleSecurityException(SecurityException ex) {
        log.warn("权限校验失败: {}", ex.getMessage());
        return Result.error("权限不足");
    }

    /**
     * 处理访问拒绝异常
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleAccessDenied(AccessDeniedException ex) {
        log.warn("访问被拒绝: {}", ex.getMessage());
        return Result.error(MessageConstant.PERMISSION_DENIED);
    }

    /**
     * 处理资源不存在异常
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("资源不存在: {}", ex.getMessage());
        return Result.error("请求的资源不存在");
    }

    /**
     * 处理数据完整性约束违反异常
     * 如: 唯一键重复、外键约束等
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("Duplicate entry")) {
            log.error("数据重复: {}", message);
            String[] split = message.split(" ");
            String entry = split.length > 2 ? split[2] : "数据";
            return Result.error(entry + MessageConstant.ALREADY_EXISTS);
        }
        log.error("数据完整性约束违反: {}", message);
        return Result.error("数据操作失败");
    }

    /**
     * 处理 SQL 异常
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleSQLException(SQLException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("Duplicate entry")) {
            log.error("SQL唯一约束违反: {}", message);
            String[] split = message.split(" ");
            String entry = split.length > 2 ? split[2] : "数据";
            return Result.error(entry + "已存在");
        }
        log.error("SQL异常: ", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /**
     * 处理所有未捕获的异常（兜底）
     *
     * @param ex
     * @return
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception ex) {
        log.error("未捕获的异常: ", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }
}
