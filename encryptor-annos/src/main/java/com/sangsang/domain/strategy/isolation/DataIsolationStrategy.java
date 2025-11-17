package com.sangsang.domain.strategy.isolation;

import com.sangsang.domain.enums.IsolationRelationEnum;

import java.util.Arrays;
import java.util.List;

/**
 * 数据隔离策略
 * 注意：泛型T允许的值在 com.sangsang.domain.strategy.isolation.DataIsolationStrategy#ALLOW_TYPES
 *
 * @author liutangqi
 * @date 2025/6/12 16:09
 */
public interface DataIsolationStrategy<T> {

    /**
     * 获取数据隔离的数据库字段名
     * 注意：这里返回的是数据库的表字段名，不是实体类的字段名
     * 这里返回的值是空的，则不进行数据隔离
     *
     * @author liutangqi
     * @date 2025/6/21 19:23
     * @Param [tableName 表名]
     **/
    String getIsolationField(String tableName);

    /**
     * 获取表字段和具体隔离字段的关系
     *
     * @author liutangqi
     * @date 2025/6/21 19:24
     * @Param [tableName 表名]
     **/
    IsolationRelationEnum getIsolationRelation(String tableName);


    /**
     * 获取数据隔离的具体数据
     * 注意：泛型T允许的值在 com.sangsang.domain.strategy.isolation.DataIsolationStrategy#ALLOW_TYPES
     *
     * @author liutangqi
     * @date 2025/6/21 19:49
     * @Param [tableName 表名]
     **/
    T getIsolationData(String tableName);

    /**
     * 泛型 T 允许的值
     *
     * @author liutangqi
     * @date 2025/6/21 19:28
     * @Param
     **/
    List<Class> ALLOW_TYPES = Arrays.asList(
            String.class,
            Long.class,
            Integer.class,
            List.class//注意：list里面只允许String ,Integer,Long 中的一种
    );
}
