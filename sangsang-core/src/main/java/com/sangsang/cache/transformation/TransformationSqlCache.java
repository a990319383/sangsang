package com.sangsang.cache.transformation;

import com.sangsang.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * 缓存不需要进行语法转换的sql信息
 *
 * @author liutangqi
 * @date 2025/5/21 10:05
 */
public class TransformationSqlCache {

    /**
     * 记录不需要进行语法转换的sql集合
     * 存储的是com.sangsang.util.StringUtils#getSqlUniqueKey(sql)
     * 备注：采用原sql长度_sha256 发生冲突的概率可以忽略不计
     *
     * @author liutangqi
     * @date 2025/5/21 10:24
     * @Param
     **/
    private static final Set<String> needlessTransformationSql = new HashSet<>();

    /**
     * 新增不需要语法转换的sql
     *
     * @author liutangqi
     * @date 2025/5/21 10:26
     * @Param [namespace]
     **/
    public static void addNeedlessTransformationSql(String sql) {
        String value = StringUtils.getSqlUniqueKey(sql);
        needlessTransformationSql.add(value);
    }

    /**
     * 判断当前sql是否一定不需要语法转换
     *
     * @author liutangqi
     * @date 2025/5/21 10:26
     * @Param [namespace]
     **/
    public static boolean isNeedlessTransformationSql(String sql) {
        String value = StringUtils.getSqlUniqueKey(sql);
        return needlessTransformationSql.contains(value);
    }

}
