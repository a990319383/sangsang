package com.sangsang.demo.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVo {
    private Long id;
    private String userName;
    private String loginName;
    private String loginPwd;
    private String phone;
    private String orgSeq;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
