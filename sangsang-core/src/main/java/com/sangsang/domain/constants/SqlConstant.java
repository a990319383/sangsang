package com.sangsang.domain.constants;

/**
 * DML语法相关常量
 *
 * @author liutangqi & gpt-5.6-sol
 * @date 2026/11/13 10:00
 */
public interface SqlConstant {
    /**
     * SQL Server OUTPUT语法中表示更新后数据的伪表
     */
    String INSERTED = "inserted";

    /**
     * SQL Server OUTPUT语法中表示更新前数据的伪表
     */
    String DELETED = "deleted";
}
