package com.sangsang.mockentity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * mock实体类，主要展示表结构，所以没有getter setter
 */
@TableName(value = "tb_user_bak")
public class UserBakEntity extends BaseEntity {

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
     * 注意：为了某些测试条件，这个电话号码是明文，区别于UserEntity的phone字段
     */
    @TableField(value = "phone")
    private String phone;

    /**
     * 角色id
     */
    @TableField(value = "role_id")
    private Long roleId;

}