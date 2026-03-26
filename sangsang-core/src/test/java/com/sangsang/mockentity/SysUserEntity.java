package com.sangsang.mockentity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sangsang.domain.annos.isolation.DataIsolation;
import com.sangsang.domain.enums.IsolationConditionalRelationEnum;
import com.sangsang.strategy.Test222DataIsolationStrategy;
import com.sangsang.strategy.TestDataIsolationStrategy;

/**
 * 系统用户表
 *
 * @author hgwlpt
 */
@TableName("sys_user")
@DataIsolation(conditionalRelation = IsolationConditionalRelationEnum.OR, value = {TestDataIsolationStrategy.class, Test222DataIsolationStrategy.class})
public class SysUserEntity {
    /**
     * 主键
     */
    private Long id;

    /**
     * 角色id
     */
    private Long roleId;

    /**
     * 登录名
     */
    private String loginName;

    /**
     * 密码
     */
    private String password;

    /**
     * 用户名
     */
    private String name;

    /**
     * 手机号
     */
    private String mobile;
}