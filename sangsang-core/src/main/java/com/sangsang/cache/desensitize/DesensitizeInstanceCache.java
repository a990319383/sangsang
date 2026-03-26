package com.sangsang.cache.desensitize;

import com.sangsang.config.other.DefaultBeanPostProcessor;
import com.sangsang.domain.exception.DesensitizeException;
import com.sangsang.domain.strategy.desensitize.DesensitizeStrategy;
import com.sangsang.domain.wrapper.ClassHashMapWrapper;

import java.util.*;

/**
 * @author liutangqi
 * @date 2025/4/12 23:14
 */
public class DesensitizeInstanceCache extends DefaultBeanPostProcessor {

    //缓存脱敏对象，避免重复创建大量对象
    private final static Map<Class, DesensitizeStrategy> INSTANCE_MAP = new ClassHashMapWrapper<>();


    /**
     * 初始化spring容器中的脱敏策略
     *
     * @author liutangqi
     * @date 2025/7/16 17:08
     * @Param [strategies]
     **/
    public void init(List<DesensitizeStrategy> strategies) {
        List<DesensitizeStrategy> desensitizeStrategies = Optional.ofNullable(strategies).orElse(new ArrayList<>());
        for (DesensitizeStrategy strategy : desensitizeStrategies) {
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
    public static final DesensitizeStrategy getInstance(Class<? extends DesensitizeStrategy> clazz) {
        //1.先从本地缓存好的里面找
        DesensitizeStrategy instance = INSTANCE_MAP.get(clazz);

        //2.本地缓存找不到，根据无参构造进行实例化，然后放缓存
        if (instance == null) {
            try {
                instance = clazz.newInstance();
                INSTANCE_MAP.put(clazz, instance);
            } catch (Exception e) {
                throw new DesensitizeException(String.format("脱敏策略无参实例化失败 %s", clazz.getName()));
            }

        }
        return instance;
    }

}
