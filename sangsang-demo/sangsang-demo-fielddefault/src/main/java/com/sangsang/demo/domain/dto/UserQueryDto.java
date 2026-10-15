package com.sangsang.demo.domain.dto;

import lombok.Data;

@Data
public class UserQueryDto {
    private String userName;
    private String phone;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
