package com.sangsang.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 组织权限过滤的时候的关系
 *
 * @author liutangqi
 * @date 2025/6/12 17:10
 */
@AllArgsConstructor
@Getter
public enum IsolationRelationEnum {

    EQUALS("equals", "数据隔离时使用= 栗如: org_seq = 'xxx'"),
    LIKE_PREFIX("likePrefix", "数据隔离时使用前缀匹配 栗如: org_seq like 'xxx%'"),
    IN("in", "数据隔离时使用 in (xxx,xxx)"),
    ;

    /**
     * 唯一的标识符
     * 配置文件就是通过这个code匹配具体的策略的
     */
    private String code;

    /**
     * 描述
     */
    private String desc;
}
