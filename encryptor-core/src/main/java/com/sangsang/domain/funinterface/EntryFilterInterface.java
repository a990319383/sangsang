package com.sangsang.domain.funinterface;

import java.util.Map;

/**
 * @author liutangqi
 * @date 2025/11/17 9:54
 */
@FunctionalInterface
public interface EntryFilterInterface<T> {
    /**
     * 是否保留这个entry
     * true: 保留
     * false: 剔除掉
     *
     * @author liutangqi
     * @date 2025/11/17 9:55
     * @Param [entry]
     **/
    boolean retain(Map.Entry<String, T> entry);
}
