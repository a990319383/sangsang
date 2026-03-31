package com.sangsang.demo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.annos.isolation.DataIsolation;

import java.time.LocalDateTime;

/**
 * 实体类
 * sangsang启动时会扫描实体类，将需要加密的表字段缓存到本地
 */
@TableName(value = "demo_user")
@DataIsolation
public class DemoUserEntity {
    /**
     * 主键
     */
    @TableId
    private Long id;

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
     * 组织的全路径（上级的上级权限/上级权限/本级权限）
     */
    @TableField(value = "org_seq")
    private String orgSeq;

    /**
     * 创建时间
     */
    //不标注@TableField的话，会默认驼峰转下划线作为字段名
    private LocalDateTime createTime;
    /**
     * 修改时间
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}