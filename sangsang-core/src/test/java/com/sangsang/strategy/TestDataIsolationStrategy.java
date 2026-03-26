package com.sangsang.strategy;

import com.sangsang.domain.enums.IsolationRelationEnum;
import com.sangsang.domain.strategy.isolation.DataIsolationStrategy;

/**
 * 测试时获取当前数据隔离的值
 *
 * @author liutangqi
 * @date 2025/6/13 15:12
 */
public class TestDataIsolationStrategy implements DataIsolationStrategy<Long> {

    @Override
    public String getIsolationField(String tableName) {
        return "role_id";
    }

    @Override
    public IsolationRelationEnum getIsolationRelation(String tableName) {
        return IsolationRelationEnum.EQUALS;
    }

    @Override
    public Long getIsolationData(String tableName) {
        return 1111111L;
    }

}
