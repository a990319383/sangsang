package com.sangsang.demo.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试pojo模式接口映射，这里将所有变量名改成下划线
 *
 * @author liutangqi
 * @date 2026/8/14 16:52
 * @Param
 **/
@Data
public class UserUnderLineVo {
    /**
     * 主键
     */
    private Long id;
    /**
     * 用户名
     * PS:下划线里面大小写是随便写的
     */
    private String uSer_Name;

    /**
     * 登录名
     * PS:下划线全小写
     */
    private String login_name;

    /**
     * 登录密码
     * PS:下划线全大写
     */
    private String LOGIN_PWD;

    /**
     * 电话
     */
    private String phone;

    /**
     * 组织的全路径（上级的上级权限/上级权限/本级权限）
     */
    private String org_seq;

    /**
     * 创建时间
     */
    private LocalDateTime create_time;
    /**
     * 修改时间
     */
    private LocalDateTime update_time;
}
