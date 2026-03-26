package com.sangsang.domain.constants;

import net.sf.jsqlparser.expression.LongValue;

/**
 * @author liutangqi
 * @date 2024/3/4 10:28
 */
public interface NumberConstant {
    int ZERO = 0;
    int ONE = 1;
    int EIGHT = 8;
    int HUNDRED = 100;
    int FIVE_HUNDRED = 500;
    int NEGATIVE_ONE = -1;

    LongValue NEGATIVE_ONE_LONG_VALUE = new LongValue(-1);
    LongValue ZERO_LONG_VALUE = new LongValue(ZERO);
    LongValue ONE_LONG_VALUE = new LongValue(ONE);
}
