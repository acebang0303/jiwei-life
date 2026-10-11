package com.jiwei.common.exception;

/**
 * 不允许删除异常
 */
public class DeletionNotAllowedException extends BaseException {
    public DeletionNotAllowedException(String msg) { super(msg); }
}
