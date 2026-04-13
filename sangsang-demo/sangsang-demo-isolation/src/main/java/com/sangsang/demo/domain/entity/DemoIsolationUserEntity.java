package com.sangsang.demo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sangsang.demo.strategies.OrgIsolationStrategy;
import com.sangsang.domain.annos.isolation.DataIsolation;

import java.time.LocalDateTime;

/**
 * 实体类
 * sangsang启动时会扫描实体类，将需要加密的表字段缓存到本地
 * 备注1：我可以getter setter都没有，仅作为配置标注
 * 备注2：mapper方法，service方法的返回值也和我没关系
 */
@TableName(value = "demo_isolation_user")//这个类没必要用于mapper，service的入参/返回值
@DataIsolation(OrgIsolationStrategy.class)
public class DemoIsolationUserEntity {
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