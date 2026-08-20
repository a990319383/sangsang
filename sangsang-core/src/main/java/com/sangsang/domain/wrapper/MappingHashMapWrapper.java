package com.sangsang.domain.wrapper;

import cn.hutool.core.text.NamingCase;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.dto.IgnoreCaseCacheKey;
import com.sangsang.domain.exception.FieldException;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.StringUtils;
import lombok.ToString;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 装饰器模式将HashMap<String,T> 进行一次包装
 * 用于处理pojo模式查询结果解密的字段映射匹配
 * key: xml中sql的字段名
 * 将这个key原始不区分大小写存了一份，转换为驼峰不区分大小写存了一份，取值的时候会根据当前的配置情况从对应容器中获取
 * value：originalMap和humpMap存储的值都是一样的
 * 注意：这里的keySet() entrySet() 返回的都是新的引用信息，entry的key无法修改，所以无法复用之前的引用，所以是无法使用迭代器对里面的元素进行删除的，使用时需要注意
 *
 * @author liutangqi
 * @date 2026/8/18 17:13
 */
@ToString
public class MappingHashMapWrapper<T> implements Map<String, T>, Serializable {

    /**
     * xml中配置的resultMap
     * key: column  (xml中sql的字段名)
     * value: property （接收结果的java变量名）
     * 注意：实测中column是大小写不敏感的
     */
    private final Map<IgnoreCaseCacheKey, String> resultMap = new HashMap<>();

    /**
     * 将原始的xml的sql的字段进行转驼峰处理后存储
     * key:不区分大小写的驼峰
     */
    private final Map<IgnoreCaseCacheKey, T> humpMap = new HashMap<>();

    /**
     * 存储xml中sql原始字段的值
     * key:不区分大小写的原始字段的值
     * PS:这里可能是下划线，也可能是驼峰，xml中的sql的写成什么样就是什么样
     */
    private final Map<IgnoreCaseCacheKey, T> originalMap = new HashMap<>();


    /**
     * 构造方法，当前xml如果配置resultMap的话，传入配置的resultMap的映射关系
     *
     * @author liutangqi
     * @date 2026/8/19 14:21
     * @Param [resultMap]
     **/
    public MappingHashMapWrapper(Map<String, String> resultMap) {
        if (CollectionUtils.isEmpty(resultMap)) {
            return;
        }
        for (Map.Entry<String, String> entry : resultMap.entrySet()) {
            this.resultMap.put(IgnoreCaseCacheKey.buildKey(entry.getKey()), entry.getValue());
        }
    }

    public MappingHashMapWrapper() {
    }

    @Override
    public int size() {
        return this.originalMap.size();
    }

    @Override
    public boolean isEmpty() {
        return this.originalMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        //空值校验
        if (key == null) {
            return false;
        }
        //校验key的类型
        typeCheck(key);

        //原始key类型转换
        IgnoreCaseCacheKey ignoreCaseCacheKey = IgnoreCaseCacheKey.buildKey((String) key);

        //通过原始的key从resultMap中看是否有映射的key，如果存在映射的key的话，需要使用映射的key去取值
        String resultMapKey = this.resultMap.get(ignoreCaseCacheKey);
        if (StringUtils.isNotBlank(resultMapKey)) {
            ignoreCaseCacheKey = IgnoreCaseCacheKey.buildKey(resultMapKey);
        }

        //查询当前mybatis配置是否开启了下换线自动转驼峰，开启的话，就两个map中有一个有这个key就算有
        if (TableCache.getSqlSessionFactoryConfig().getMapUnderscoreToCamelCase()) {
            return this.humpMap.containsKey(ignoreCaseCacheKey) || this.originalMap.containsKey(ignoreCaseCacheKey);
        }

        //未开启下划线自动转驼峰的话，就只在原始的Map中找
        return this.originalMap.containsKey(ignoreCaseCacheKey);
    }

    @Override
    public boolean containsValue(Object value) {
        return this.originalMap.containsValue(value);
    }

    @Override
    public T get(Object key) {
        //空值校验
        if (key == null) {
            return null;
        }

        //校验key的类型
        typeCheck(key);

        //真正使用的key
        String genuineKey = (String) key;

        //通过原始的key从resultMap中看是否有映射的key，如果存在映射的key的话，需要使用映射的key去存值
        String resultMapKey = this.resultMap.get(IgnoreCaseCacheKey.buildKey(genuineKey));
        if (StringUtils.isNotBlank(resultMapKey)) {
            genuineKey = resultMapKey;
        }

        //类型转换，构建忽略大小写的key
        IgnoreCaseCacheKey ignoreCaseCacheKey = IgnoreCaseCacheKey.buildKey(genuineKey);

        //开启了下划线自动转驼峰的话，优先从原始map中取，取不到就从驼峰map中取
        if (TableCache.getSqlSessionFactoryConfig().getMapUnderscoreToCamelCase()) {
            return Optional.ofNullable(this.originalMap.get(ignoreCaseCacheKey))
                    .orElseGet(() -> this.humpMap.get(ignoreCaseCacheKey));
        }

        //未开启下换线自动转驼峰，则只从原始的map中取
        return this.originalMap.get(ignoreCaseCacheKey);
    }

    @Override
    public T put(String key, T value) {
        //空值校验
        if (StringUtils.isBlank(key)) {
            throw new FieldException("MappingHashMapWrapper key 不能为空");
        }

        //真正使用的key
        String genuineKey = key;

        //通过原始的key从resultMap中看是否有映射的key，如果存在映射的key的话，需要使用映射的key去存值
        String resultMapKey = this.resultMap.get(IgnoreCaseCacheKey.buildKey(genuineKey));
        if (StringUtils.isNotBlank(resultMapKey)) {
            genuineKey = resultMapKey;
        }

        //转驼峰后存一份
        this.humpMap.put(IgnoreCaseCacheKey.buildKey(NamingCase.toCamelCase(genuineKey)), value);

        //原始的map中存一份
        return this.originalMap.put(IgnoreCaseCacheKey.buildKey(genuineKey), value);

    }

    @Override
    public T remove(Object key) {
        //空值校验
        if (key == null) {
            return null;
        }
        //校验key的类型
        typeCheck(key);

        //真正使用的key
        String genuineKey = (String) key;

        //通过原始的key从resultMap中看是否有映射的key，如果存在映射的key的话，需要使用映射的key去移除
        String resultMapKey = this.resultMap.get(IgnoreCaseCacheKey.buildKey(genuineKey));
        if (StringUtils.isNotBlank(resultMapKey)) {
            genuineKey = resultMapKey;
        }

        //转驼峰后移除掉
        this.humpMap.remove(IgnoreCaseCacheKey.buildKey(NamingCase.toCamelCase(genuineKey)));

        //原始的map移除掉
        return this.originalMap.remove(IgnoreCaseCacheKey.buildKey(genuineKey));
    }

    @Override
    public void putAll(Map<? extends String, ? extends T> m) {
        for (Entry<? extends String, ? extends T> entry : m.entrySet()) {
            this.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        //原始的清除掉
        this.originalMap.clear();
        //驼峰的清除掉
        this.humpMap.clear();
    }

    /**
     * 注意：这里根据配置，如果开启了自动下划线转驼峰的话，就把原始的和驼峰的都返回
     *
     * @author liutangqi
     * @date 2026/8/19 15:41
     * @Param []
     **/
    @Override
    public Set<String> keySet() {
        Set<String> originalMapKeySet = this.originalMap.keySet().stream().map(IgnoreCaseCacheKey::getCacheKey).collect(Collectors.toSet());
        if (!TableCache.getSqlSessionFactoryConfig().getMapUnderscoreToCamelCase()) {
            return originalMapKeySet;
        }
        Set<String> humpMapKeySet = this.humpMap.keySet().stream().map(IgnoreCaseCacheKey::getCacheKey).collect(Collectors.toSet());
        return Stream.concat(originalMapKeySet.stream(), humpMapKeySet.stream()).collect(Collectors.toSet());
    }

    /**
     * 注意：原始的和转驼峰的value存储的内容都是一样的，这里只返回原始的即可
     *
     * @author liutangqi
     * @date 2026/8/19 15:41
     * @Param []
     **/
    @Override
    public Collection<T> values() {
        return this.originalMap.values();
    }

    /**
     * 注意：这里根据配置，如果开启了自动下划线转驼峰的话，就把原始的和驼峰的都返回
     *
     * @author liutangqi
     * @date 2026/8/19 15:41
     * @Param []
     **/
    @Override
    public Set<Entry<String, T>> entrySet() {
        Set<Entry<String, T>> originalMapEntrySet = this.originalMap.entrySet().stream().map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey().getCacheKey(), entry.getValue())).collect(Collectors.toSet());
        if (!TableCache.getSqlSessionFactoryConfig().getMapUnderscoreToCamelCase()) {
            return originalMapEntrySet;
        }
        Set<Entry<String, T>> humpMapEntrySet = this.humpMap.entrySet().stream().map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey().getCacheKey(), entry.getValue())).collect(Collectors.toSet());
        return Stream.concat(originalMapEntrySet.stream(), humpMapEntrySet.stream()).collect(Collectors.toSet());
    }


    /**
     * 校验obj的类型是否是String
     *
     * @author liutangqi
     * @date 2026/8/18 17:13
     * @Param [obj]
     **/
    private void typeCheck(Object obj) {
        if (!(obj instanceof String)) {
            throw new FieldException("MappingHashMapWrapper key 必须是String类型的");
        }
    }

}