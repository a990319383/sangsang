package com.sangsang.demo.strategies;

import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.domain.strategy.fielddefault.FieldDefaultStrategy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UpdateTimeStrategy implements FieldDefaultStrategy<LocalDateTime> {
    @Override
    public boolean whetherToHandle(SqlCommandEnum sqlCommandEnum) {
        return SqlCommandEnum.INSERT.equals(sqlCommandEnum)
                || SqlCommandEnum.UPDATE.equals(sqlCommandEnum);
    }

    @Override
    public LocalDateTime getDefaultValue() {
        return LocalDateTime.now();
    }
}
