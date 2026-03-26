package com.sangsang.domain.exception;

/**
 * 字段修改新增时设置默认值异常
 *
 * @author liutangqi
 * @date 2025/7/16 17:47
 * @Param
 **/
public class FieldDefaultException extends RuntimeException {

    public FieldDefaultException(String message) {
        super(message);
    }
}
