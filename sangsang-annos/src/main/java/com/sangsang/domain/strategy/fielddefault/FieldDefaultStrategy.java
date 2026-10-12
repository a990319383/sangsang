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
     * 会根据mandatoryOverride的配置和原sql语句中是否维护了对应字段，来决定是否用这个方法的返回值覆盖掉原sql语句中的字段值
     * 原sql中没有维护这个字段或者维护的这个字段为null： 会用方法返回值去覆盖原sql语句的值
     * 原sql中维护了这个字段，且不为null: mandatoryOverride为true：用方法返回值覆盖原sql语句的值， mandatoryOverride为false：不覆盖原sql语句的值
     * 备注：如果set的值或者insert的值对应的是子查询，一律视为非null处理
     *
     * @author liutangqi
     * @date 2025/7/9 14:10
     **/
    T getDefaultValue();

}
