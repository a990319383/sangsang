package com.sangsang.domain.exception;

/**
 * 数据隔离相关异常
 *
 * @author liutangqi
 * @date 2025/6/13 11:18
 **/
public class IsolationException extends RuntimeException {

    public IsolationException(String message) {
        super(message);
    }
}
