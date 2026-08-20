package com.sangsang.demo.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 为验证mybatis resultMap映射关系特殊写法的类
 *
 * @author liutangqi
 * @date 2026/8/14 15:50
 */
@Data
public class UserResultMapVo {
    /**
     * 主键
     * PS:这里特意写成全大写
     */
    private Long ID;
    /**
     * 用户名
     * PS:这里特意写成不是驼峰，随便的大小写顺序
     */
    private String uSERNaMe;

    /**
     * 电话
     * PS:这里特意写成和表字段的phone字段不同的另外一个单词
     */
    private String mobilePhone;

    /**
     * 登录名
     */
    private String loginName;

    /**
     * 登录密码
     */
    private String loginPwd;

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
