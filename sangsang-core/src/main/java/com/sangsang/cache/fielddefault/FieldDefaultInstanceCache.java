package com.sangsang.cache.fielddefault;

import com.sangsang.config.other.DefaultBeanPostProcessor;
import com.sangsang.domain.exception.FieldDefaultException;
import com.sangsang.domain.strategy.fielddefault.FieldDefaultStrategy;
import com.sangsang.domain.wrapper.ClassHashMapWrapper;

import java.util.*;

/**
 * 数据默认值实例缓存
 *
 * @author liutangqi
 * @date 2025/7/15 18:16
 */
public class FieldDefaultInstanceCache extends DefaultBeanPostProcessor {
    /**
     * 缓存实例
     **/
    private static final Map<Class, FieldDefaultStrategy> INSTANCE_MAP = new ClassHashMapWrapper<>();

    /**
     * 初始化spring容器中目的数据默认值获取策略实例
     *
     * @author liutangqi
     * @date 2025/7/16 17:42
     * @Param [strategies]
     **/
    public void init(List<FieldDefaultStrategy> strategies) {
        //初始化实例
        List<FieldDefaultStrategy> fieldDefaultStrategies = Optional.ofNullable(strategies).orElse(new ArrayList<>());
        for (FieldDefaultStrategy strategy : fieldDefaultStrategies) {
            INSTANCE_MAP.put(strategy.getClass(), strategy);
        }
    }

    /**
     * 获取实例
     *
     * @author liutangqi
     * @date 2025/7/16 17:11
     * @Param [clazz]
     **/
    public static final FieldDefaultStrategy getInstance(Class<? extends FieldDefaultStrategy> clazz) {
        //1.先从本地缓存好的里面找
        FieldDefaultStrategy instance = INSTANCE_MAP.get(clazz);

        //2.本地缓存找不到，根据无参构造进行实例化，然后放缓存
        if (instance == null) {
            try {
                instance = clazz.newInstance();
                INSTANCE_MAP.put(clazz, instance);
            } catch (Exception e) {
                throw new FieldDefaultException(String.format("字段新增删除默认值的策略无参构造实例化失败 %s", clazz.getName()));
            }
        }
        return instance;
    }

}
