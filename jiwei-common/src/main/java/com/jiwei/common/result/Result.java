package com.jiwei.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果
 * @param <T> 数据类型
 */
@Data
@AllArgsConstructor
public class Result<T> implements Serializable {
    //编码：1成功，0和其它数字为失败
    private Integer code;
    //错误信息
    private String msg;
    //数据
    private T data;

    /**
     * 成功，无数据返回
     */
    public static <T> Result<T> success() {
        return new Result<>(1, null, null);
    }

    /**
     * 成功，带数据返回
     */
    public static <T> Result<T> success(T object) {
        return new Result<>(1, null, object);
    }

    /**
     * 失败，带错误信息
     */
    public static <T> Result<T> error(String msg) {
        return new Result<>(0, msg, null);
    }
}
