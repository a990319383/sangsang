package com.sangsang.domain.exception;

/**
 * 框架基础类异常
 *
 * @author liutangqi
 * @date 2025/11/7 9:59
 * @Param
 **/
public class FieldException extends RuntimeException {

    public FieldException(String message) {
        super(message);
    }
}
