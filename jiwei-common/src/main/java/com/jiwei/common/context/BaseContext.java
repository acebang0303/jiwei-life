package com.jiwei.common.context;

/**
 * ThreadLocal存储当前登录用户信息
 */
public class BaseContext {
    private static final ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    /**
     * 设置当前线程的用户ID
     * @param id 用户ID
     */
    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    /**
     * 获取当前线程的用户ID
     * @return 用户ID
     */
    public static Long getCurrentId() {
        return threadLocal.get();
    }

    /**
     * 清除当前线程的用户ID
     */
    public static void removeCurrentId() {
        threadLocal.remove();
    }
}
