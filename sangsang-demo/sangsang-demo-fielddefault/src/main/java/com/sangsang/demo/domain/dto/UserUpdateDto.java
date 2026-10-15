package com.sangsang.demo.domain.dto;

import lombok.Data;

@Data
public class UserUpdateDto {
    private Long id;
    private String userName;
    private String loginName;
    private String loginPwd;
    private String phone;
    private String orgSeq;
}
