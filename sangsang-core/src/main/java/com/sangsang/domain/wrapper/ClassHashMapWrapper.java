package com.sangsang.domain.wrapper;

import com.sangsang.domain.dto.ClassCacheKey;
import com.sangsang.domain.exception.FieldException;
import com.sangsang.util.ReflectUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 装饰器模式将HashMap<Class,T>进行一次包装，对于Class的存取值时，将Class包装成ClassCacheKey 根据实际情况，看是否需要忽略类加载器和代理，只要类全限定名一样，就是同一个类
 * 有些本地热部署等场景会导致类加载器不同，存取值出现问题
 *
 * @author liutangqi
 * @date 2025/12/1 14:31
 */
@Slf4j
public class ClassHashMapWrapper<T> implements Map<Class, T>, Serializable {

    private final Map<ClassCacheKey, T> classCacheMap = new HashMap<>();


    @Override
    public int size() {
        return this.classCacheMap.size();
    }

    @Override
    public boolean isEmpty() {
        return this.classCacheMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        //空值校验
        if (key == null) {
            return false;
        }
        //校验key的类型
        typeCheck(key);
        //将String包装成ClassCacheKey
        return this.classCacheMap.containsKey(ClassCacheKey.buildKey((Class) key));
    }

    @Override
    public boolean containsValue(Object value) {
        return this.classCacheMap.containsValue(value);
    }

    @Override
    public T get(Object key) {
        //空值校验
        if (key == null) {
            return null;
        }
        //校验key的类型
        typeCheck(key);
        //将Class 包装成ClassCacheKey
        return classCacheMap.get(ClassCacheKey.buildKey((Class) key));
    }

    @Override
    public T put(Class key, T value) {
        //空值校验
        if (key == null) {
            throw new FieldException("ClassHashMapWrapper key 不能为空");
        }
        return this.classCacheMap.put(ClassCacheKey.buildKey(key), value);
    }

    @Override
    public T remove(Object key) {
        //空值校验
        if (key == null) {
            return null;
        }
        //校验key的类型
        typeCheck(key);
        //将String 包装成ClassCacheKey
        return this.classCacheMap.remove(ClassCacheKey.buildKey((Class) key));
    }

    @Override
    public void putAll(Map<? extends Class, ? extends T> m) {
        for (Entry<? extends Class, ? extends T> entry : m.entrySet()) {
            this.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        this.classCacheMap.clear();
    }

    @Override
    public Set<Class> keySet() {
        return this.classCacheMap.keySet().stream().map(ClassCacheKey::getClassName).map(ReflectUtils::forName).collect(Collectors.toSet());
    }

    @Override
    public Collection<T> values() {
        return this.classCacheMap.values();
    }

    @Override
    public Set<Entry<Class, T>> entrySet() {
        return this.classCacheMap.entrySet().stream().map(entry -> new AbstractMap.SimpleEntry<>(ReflectUtils.forName(entry.getKey().getClassName()), entry.getValue())).collect(Collectors.toSet());
    }


    /**
     * 校验obj的类型是否是Class
     *
     * @author liutangqi
     * @date 2025/11/7 10:01
     * @Param [obj]
     **/
    private void typeCheck(Object obj) {
        if (!(obj instanceof Class)) {
            throw new FieldException("ClassHashMapWrapper key 必须是Class类型的");
        }
    }

}
