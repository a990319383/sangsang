package com.sangsang.demo.threadlocal;

import com.sangsang.demo.domain.bo.SqlLogBo;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录当前执行中的sql
 *
 * @author liutangqi
 * @date 2026/3/31 17:13
 */
public class SqlHolder {
    /**
     * 记录当前请求中的sql
     */
    private static final ThreadLocal<List<SqlLogBo>> sqlHolder = new ThreadLocal<>();

    /**
     * 记录当前的sql
     *
     * @author liutangqi
     * @date 2026/3/31 17:17
     * @Param [oldSql, newSql]
     **/
    public static void recordSql(String oldSql, String newSql) {
        List<SqlLogBo> sqlLists = sqlHolder.get();
        if (sqlLists == null) {
            sqlLists = new ArrayList<>();
            sqlHolder.set(sqlLists);
        }
        sqlLists.add(new SqlLogBo(oldSql, newSql));
    }

    /**
     * 获取当前执行的sql
     * 并清空
     *
     * @author liutangqi
     * @date 2026/3/31 17:19
     * @Param []
     **/
    public static List<SqlLogBo> getSqls() {
        return sqlHolder.get();
    }

    /**
     * 清空当前执行的sql
     *
     * @author liutangqi
     * @date 2026/3/31 17:40
     * @Param []
     **/
    public static void clear() {
        sqlHolder.remove();
    }
}
