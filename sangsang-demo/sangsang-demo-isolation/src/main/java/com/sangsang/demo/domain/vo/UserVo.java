package com.sangsang.demo.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 为了对框架有个更直观的体验，这里创建一个VO类，不直接使用实体类
 *
 * @author liutangqi
 * @date 2026/3/30 17:39
 */
@Data
public class UserVo {
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
     * 组织的全路径（上级的上级权限/上级权限/本级权限）
     */
    private String orgSeq;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 修改时间
     */
    private LocalDateTime updateTime;
}
