package com.sangsang.domain.exception;

/**
 * sql语法格式转换异常
 *
 * @author liutangqi
 * @date 2025/5/23 18:21
 */
public class TransformationException extends RuntimeException {

    public TransformationException(String message) {
        super(message);
    }
}
