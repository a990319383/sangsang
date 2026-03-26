package com.sangsang.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据隔离时，不同的表直接的隔离字段之间的关系
 *
 * @author liutangqi
 * @date 2025/8/15 15:46
 */
@AllArgsConstructor
@Getter
public enum IsolationConditionalRelationEnum {
    AND("不同的数据隔离条件之间使用and进行连接"),
    OR("不同的数据隔离条件之间使用or进行连接"),
    ;

    /**
     * 描述
     */
    private String desc;

}
