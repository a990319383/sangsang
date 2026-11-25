package com.sangsang.domain.constants;

import java.util.Arrays;
import java.util.List;

/**
 * DML语法相关常量
 *
 * @author liutangqi & gpt-5.6-sol
 * @date 2026/11/13 10:00
 */
public interface SqlConstant {
    /**
     * SQL Server OUTPUT语法
     * 中表示更新后数据的伪表
     * 注意：PostgreSQL 的 RETURNING 是不存在这个伪表的
     */
    String SQL_SERVER_INSERTED = "inserted";

    /**
     * SQL Server OUTPUT语法
     * 中表示更新前数据的伪表
     * 注意：PostgreSQL 的 RETURNING 是不存在这个伪表的
     */
    String SQL_SERVER_DELETED = "deleted";

    /**
     * DML返回值里面用的伪表的伪别名
     * 注意：PostgreSQL 的 RETURNING 是不存在这个伪表的
     */
    List<String> SQL_SERVER_RETURN_TABLES = Arrays.asList(SQL_SERVER_INSERTED, SQL_SERVER_DELETED);
}
