package com.sangsang.domain.wrapper;

import com.sangsang.domain.dto.FieldCacheKey;
import com.sangsang.domain.exception.FieldException;
import com.sangsang.domain.funinterface.EntryFilterInterface;
import com.sangsang.util.StringUtils;
import lombok.ToString;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 装饰器模式将HashMap<String,T> 进行一次包装，其中的key 进行大小写敏感处理，忽略关键字符的处理
 * 注意：这里的keySet() entrySet() 返回的都是新的引用信息，entry的key无法修改，所以无法复用之前的引用，所以是无法使用迭代器对里面的元素进行删除的，使用时需要注意
 *
 * @author liutangqi
 * @date 2025/11/6 9:28
 */
@ToString
public class FieldHashMapWrapper<T> implements Map<String, T>, Serializable {

    private final Map<FieldCacheKey, T> fieldCacheMap = new HashMap<>();

    @Override
    public int size() {
        return this.fieldCacheMap.size();
    }

    @Override
    public boolean isEmpty() {
        return this.fieldCacheMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        //空值校验
        if (key == null) {
            return false;
        }
        //校验key的类型
        typeCheck(key);
        //将String包装成FieldCacheKey
        return this.fieldCacheMap.containsKey(FieldCacheKey.buildKey((String) key));
    }

    @Override
    public boolean containsValue(Object value) {
        return this.fieldCacheMap.containsValue(value);
    }

    @Override
    public T get(Object key) {
        //空值校验
        if (key == null) {
            return null;
        }
        //校验key的类型
        typeCheck(key);
        //将String 包装成FieldCacheKey
        return fieldCacheMap.get(FieldCacheKey.buildKey((String) key));
    }

    @Override
    public T put(String key, T value) {
        //空值校验
        if (StringUtils.isBlank(key)) {
            throw new FieldException("FieldHashMapWrapper key 不能为空");
        }
        return this.fieldCacheMap.put(FieldCacheKey.buildKey(key), value);
    }

    @Override
    public T remove(Object key) {
        //空值校验
        if (key == null) {
            return null;
        }
        //校验key的类型
        typeCheck(key);
        //将String 包装成FieldCacheKey
        return this.fieldCacheMap.remove(FieldCacheKey.buildKey((String) key));
    }

    @Override
    public void putAll(Map<? extends String, ? extends T> m) {
        for (Entry<? extends String, ? extends T> entry : m.entrySet()) {
            this.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        this.fieldCacheMap.clear();
    }

    @Override
    public Set<String> keySet() {
        return this.fieldCacheMap.keySet().stream().map(FieldCacheKey::getCacheKey).collect(Collectors.toSet());
    }

    @Override
    public Collection<T> values() {
        return this.fieldCacheMap.values();
    }

    @Override
    public Set<Entry<String, T>> entrySet() {
        return this.fieldCacheMap.entrySet().stream().map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey().getCacheKey(), entry.getValue())).collect(Collectors.toSet());
    }


    /**
     * 校验obj的类型是否是String
     *
     * @author liutangqi
     * @date 2025/11/7 10:01
     * @Param [obj]
     **/
    private void typeCheck(Object obj) {
        if (!(obj instanceof String)) {
            throw new FieldException("FieldHashMapWrapper key 必须是String类型的");
        }
    }


    /**
     * 过滤此Map的元素
     *
     * @author liutangqi
     * @date 2025/11/17 9:58
     * @Param [entryFilter]
     **/
    public void filter(EntryFilterInterface<T> entryFilter) {
        Iterator<Entry<FieldCacheKey, T>> iterator = this.fieldCacheMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<FieldCacheKey, T> entry = iterator.next();
            Map.Entry<String, T> simpleEntry = new AbstractMap.SimpleEntry<>(entry.getKey().getCacheKey(), entry.getValue());
            if (!entryFilter.retain(simpleEntry)) {
                iterator.remove();
            }
        }
    }
}