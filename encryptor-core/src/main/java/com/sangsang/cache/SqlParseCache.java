package com.sangsang.cache;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.LRUCache;
import com.sangsang.config.other.DefaultBeanPostProcessor;
import com.sangsang.config.properties.FieldProperties;
import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.util.StringUtils;
import net.sf.jsqlparser.statement.Statement;


/**
 * sql语句解析的本地缓存
 * 优先加载这个bean，避免有些@PostConstruct 处理逻辑中需要用到这个缓存，但是这个缓存还未初始化完成
 *
 * @author liutangqi
 * @date 2025/6/12 15:28
 */
public class SqlParseCache extends DefaultBeanPostProcessor {

    /**
     * 存储当前sql的解析结果的缓存
     * key: sql的长度_sha256  com.sangsang.util.StringUtils#getSqlUniqueKey()
     * value: 解析结果
     **/
    private static LRUCache<String, Statement> SQL_PARSE_CACHE = CacheUtil.newLRUCache(NumberConstant.FIVE_HUNDRED);

    /**
     * 初始化缓存
     *
     * @author liutangqi
     * @date 2025/6/12 15:40
     * @Param [fieldProperties]
     **/
    public static void init(FieldProperties fieldProperties) {
        if (fieldProperties.getLruCapacity() != null && !fieldProperties.getLruCapacity().equals(NumberConstant.FIVE_HUNDRED)) {
            SQL_PARSE_CACHE = CacheUtil.newLRUCache(fieldProperties.getLruCapacity());
        }
    }


    /**
     * 通过sql获取解析结果
     *
     * @author liutangqi
     * @date 2025/6/18 11:15
     * @Param [sql]
     **/
    public static Statement getSqlParseCache(String sql) {
        return SQL_PARSE_CACHE.get(StringUtils.getSqlUniqueKey(sql));
    }

    /**
     * 设置sql的解析结果到缓存
     *
     * @author liutangqi
     * @date 2025/6/18 11:16
     * @Param [sql, baseFieldParseTable]
     **/
    public static void setSqlParseCache(String sql, Statement statement) {
        SQL_PARSE_CACHE.put(StringUtils.getSqlUniqueKey(sql), statement);
    }
}
