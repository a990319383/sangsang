package com.sangsang.domain.strategy.fielddefault;

import com.sangsang.domain.enums.SqlCommandEnum;

/**
 * 字段新增删除默认值的策略
 * 泛型T 是返回值类型
 *
 * @author liutangqi
 * @date 2025/7/9 14:06
 */
public interface FieldDefaultStrategy<T> {

    /**
     * 是否需要处理这个默认值
     *
     * @param sqlCommandEnum 这个可能是插入，也可能是修改
     * @return true：需要处理 false:不需要处理
     * @author liutangqi
     * @date 2025/7/18 16:07
     **/
    boolean whetherToHandle(SqlCommandEnum sqlCommandEnum);

    /**
     * 获取当前的默认值
     * 当返回值不为null，并且原sql语句中这个字段没有维护值，或者维护的值为null就会动态维护sql的值
     *
     * @author liutangqi
     * @date 2025/7/9 14:10
     **/
    T getDefaultValue();

}
