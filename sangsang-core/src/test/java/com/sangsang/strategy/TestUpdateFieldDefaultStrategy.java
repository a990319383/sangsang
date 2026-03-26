package com.sangsang.strategy;

import cn.hutool.core.date.DateUtil;
import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.domain.strategy.fielddefault.FieldDefaultStrategy;

import java.time.LocalDateTime;

/**
 * @author liutangqi
 * @date 2025/7/17 17:17
 */

public class TestUpdateFieldDefaultStrategy implements FieldDefaultStrategy<String> {

    @Override
    public boolean whetherToHandle(SqlCommandEnum sqlCommandEnum) {
        return SqlCommandEnum.INSERT.equals(sqlCommandEnum) || SqlCommandEnum.UPDATE.equals(sqlCommandEnum);
    }

    @Override
    public String getDefaultValue() {
        return "2025-11-14 10:07:13";
    }
}
