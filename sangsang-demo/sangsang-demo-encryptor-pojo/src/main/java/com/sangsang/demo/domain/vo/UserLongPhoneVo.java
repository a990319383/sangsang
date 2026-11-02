package com.sangsang.demo.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试响应值不是String类型
 *
 * @author liutangqi
 * @date 2026/3/30 17:39
 */
@Data
public class UserLongPhoneVo {
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
     * 注意：这个值密文存储的，这里不是字符串类型，是Long类型
     */
    private Long phone;

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
