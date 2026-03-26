package com.sangsang.domain.enums;

import lombok.AllArgsConstructor;

/**
 * sql执行的类型
 *
 * @author liutangqi
 * @date 2025/7/9 14:20
 */
@AllArgsConstructor
public enum SqlCommandEnum {
    INSERT("插入语句"),
    UPDATE("修改语句"),
//    DELETE("删除语句"), 目前删除和查询暂未相应使用场景，暂时注释掉
//    SELECT("查询语句"),
    ;

    private String desc;
}
