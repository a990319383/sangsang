package com.sangsang.strategy;

import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.domain.strategy.fielddefault.FieldDefaultStrategy;

/**
 * 模拟只有update的时候才会维护这个字段
 *
 * @author liutangqi
 * @date 2026/10/9 18:09
 */
public class RoleIdUpdateFieldDefaultStrategy implements FieldDefaultStrategy<Long> {
    @Override
    public boolean whetherToHandle(SqlCommandEnum sqlCommandEnum) {
        return SqlCommandEnum.UPDATE.equals(sqlCommandEnum);
    }

    @Override
    public Long getDefaultValue() {
        return 11111L;
    }
}
