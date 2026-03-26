package com.sangsang.domain.exception;

/**
 * 脱敏异常
 *
 * @author liutangqi
 * @date 2025/7/16 17:47
 * @Param
 **/
public class DesensitizeException extends RuntimeException {

    public DesensitizeException(String message) {
        super(message);
    }
}
