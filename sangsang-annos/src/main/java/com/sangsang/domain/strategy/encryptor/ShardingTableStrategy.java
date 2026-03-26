package com.sangsang.domain.strategy.encryptor;

import java.util.List;

/**
 * 分表表名规则接口
 *
 * @author liutangqi
 * @date 2024/10/11 10:55
 */
@FunctionalInterface
public interface ShardingTableStrategy {
    /**
     * 根据原始表名，返回分表的所有表名的规则
     * 注意：返回值不能为null
     *
     * @author liutangqi
     * @date 2024/10/11 10:56
     * @Param [originalTableName 原始表名]
     **/
    List<String> getShardingTableName(String originalTableName);
}
