package com.sangsang.cache.encryptor;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sangsang.config.other.DefaultBeanPostProcessor;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.dto.ClassCacheKey;
import com.sangsang.domain.exception.FieldEncryptorException;
import com.sangsang.domain.strategy.DefaultStrategyBase;
import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;
import com.sangsang.domain.wrapper.ClassHashMapWrapper;
import com.sangsang.util.ClassScanerUtil;
import com.sangsang.util.ReflectUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 加解密相关策略的缓存
 * 优先加载这个bean，避免有些@PostConstruct 处理逻辑中需要用到这个缓存，但是这个缓存还未初始化完成
 *
 * @author liutangqi
 * @date 2025/6/24 17:58
 */
@Slf4j
public class EncryptorInstanceCache extends DefaultBeanPostProcessor {

    /**
     * 缓存当前项目中的pojo加解密策略
     * key: pojo加解密策略的class
     * value: 具体的实例
     **/
    private static final Map<Class, FieldEncryptorStrategy> INSTANCE_MAP = new ClassHashMapWrapper<>();

    /**
     * 初始化spring容器中的加解密策略
     *
     * @author liutangqi
     * @date 2025/6/24 11:12
     * @Param [strategies]
     **/
    public void init(List<FieldEncryptorStrategy> strategies) {
        //1.实例化默认策略
        DefaultStrategyBase.EncryptorBeanStrategy beanStrategy = new DefaultStrategyBase.EncryptorBeanStrategy(strategies);
        INSTANCE_MAP.put(DefaultStrategyBase.EncryptorBeanStrategy.class, beanStrategy);

        //2.初始化当前spring容器内的实现策略
        for (FieldEncryptorStrategy strategy : strategies) {
            INSTANCE_MAP.put(strategy.getClass(), strategy);
        }
    }


    /**
     * 获取当前加解密策略实例
     *
     * @author liutangqi
     * @date 2025/6/24 11:13
     * @Param [clazz]
     **/
    public static <T> FieldEncryptorStrategy<T> getInstance(Class<? extends FieldEncryptorStrategy> clazz) {
        //1.先从本地缓存中找
        FieldEncryptorStrategy<T> strategy = INSTANCE_MAP.get(clazz);

        //2.本地缓存找不到，尝试通过无参构造方法进行实例化
        if (strategy == null) {
            try {
                strategy = clazz.newInstance();
                INSTANCE_MAP.put(clazz, strategy);
            } catch (Exception e) {
                throw new FieldEncryptorException(String.format("未找到指定类型的加密策略 %s", clazz));
            }
        }
        return strategy;
    }


    /**
     * 测试时 mock 算法实例
     * 不从spring容器中获取，通过无参构造方法反射实例化
     *
     * @author liutangqi
     * @date 2025/6/25 17:12
     * @Param [scanBasePackage]
     **/
    public static void mockInstance(String scanBasePackage, FieldEncryptorStrategy defaultInstance) throws InstantiationException, IllegalAccessException {
        Set<Class<? extends FieldEncryptorStrategy>> strategyClasses = new HashSet<>();
        //1.扫描指定路径的实体类
        Set<Class> entityClasses = ClassScanerUtil.scan(scanBasePackage, TableName.class);

        for (Class entityClass : entityClasses) {
            //2.获取类的所有字段
            List<Field> allFields = ReflectUtils.getAllFields(entityClass);

            //3.过滤掉不属于实体类的字段，过滤掉static修饰的字段
            allFields = allFields.stream()
                    .filter(f -> f.getAnnotation(TableField.class) == null || f.getAnnotation(TableField.class).exist())
                    .filter(f -> !Modifier.isStatic(f.getModifiers()))
                    .collect(Collectors.toList());

            //4.解析字段对应数据库字段名和标注的加解密注解
            for (Field allField : allFields) {
                Optional.ofNullable(allField.getAnnotation(FieldEncryptor.class)).ifPresent(p -> strategyClasses.add(p.value()));
            }
        }

        //5.实例化这些加解密策略
        for (Class<? extends FieldEncryptorStrategy> strategyClass : strategyClasses) {
            //5.1 默认配置
            if (ClassCacheKey.classEquals(DefaultStrategyBase.EncryptorBeanStrategy.class, strategyClass)) {
                INSTANCE_MAP.put(strategyClass, defaultInstance);
            }
            //5.2实例化其它的策略
            else {
                FieldEncryptorStrategy dbFieldEncryptorStrategy = strategyClass.newInstance();
                INSTANCE_MAP.put(strategyClass, dbFieldEncryptorStrategy);
            }
        }
    }

}
