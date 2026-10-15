package com.sangsang.demo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sangsang.demo.strategies.CreateTimeStrategy;
import com.sangsang.demo.strategies.UpdateTimeStrategy;
import com.sangsang.domain.annos.fielddefault.FieldDefault;

import java.time.LocalDateTime;

/**
 * @FieldDefault 体验实体。该类主要用于被 sangsang 扫描，Mapper 的返回对象仍使用 VO。
 */
@TableName("demo_fielddefault_user")
public class DemoFieldDefaultUserEntity {
    @TableId
    private Long id;
    @TableField("user_name")
    private String userName;
    @TableField("login_name")
    private String loginName;
    @TableField("login_pwd")
    private String loginPwd;
    @TableField("phone")
    private String phone;
    @TableField("org_seq")
    private String orgSeq;

    @FieldDefault(CreateTimeStrategy.class)
    private LocalDateTime createTime;

    @FieldDefault(UpdateTimeStrategy.class)
    @TableField("update_time")
    private LocalDateTime updateTime;
}
