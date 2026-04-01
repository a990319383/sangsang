package com.sangsang.demo.domain.dto;

import lombok.Data;

/**
 * 用户修改入参
 *
 * @author liutangqi
 * @date 2026/3/31
 */
@Data
public class UserUpdateDto {
    /**
     * 主键
     */
    private Long id;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 登录名
     */
    private String loginName;

    /**
     * 登录密码
     */
    private String loginPwd;

    /**
     * 电话
     */
    private String phone;

    /**
     * 组织全路径
     */
    private String orgSeq;
}
