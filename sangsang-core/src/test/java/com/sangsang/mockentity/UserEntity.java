package com.sangsang.mockentity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.annos.isolation.DataIsolation;

/**
 * mock实体类，主要展示表结构，所以没有getter setter
 */
@TableName(value = "tb_user")
@DataIsolation
public class UserEntity extends BaseEntity {

    /**
     * 用户名
     */
    @TableField(value = "user_name")
    private String userName;

    /**
     * 登录名
     */
    @TableField(value = "login_name")
    private String loginName;

    /**
     * 登录密码
     */
    @TableField(value = "login_pwd")
    private String loginPwd;

    /**
     * 电话
     */
    @TableField(value = "phone")
    //标识这个字段是加密的字段
    @FieldEncryptor
    private String phone;

    /**
     * 角色id
     */
    @TableField(value = "role_id")
    private Long roleId;

}