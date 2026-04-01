package com.sangsang.demo.domain.dto;

import lombok.Data;

/**
 * 用户查询入参
 *
 * @author liutangqi
 * @date 2026/3/31 15:18
 */
@Data
public class UserQueryDto {
    /**
     * 用户名（模糊搜索）
     */
    private String userName;

    /**
     * 电话号码（模糊搜索）
     */
    private String phone;
}
