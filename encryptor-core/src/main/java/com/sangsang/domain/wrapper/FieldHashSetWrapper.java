package com.sangsang.domain.wrapper;

import com.sangsang.domain.dto.FieldCacheKey;
import com.sangsang.domain.exception.FieldException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 装饰器模式将Set<String> 进行一次包装，其中值进行大小写敏感处理，忽略关键字符的处理
 *
 * @author liutangqi
 * @date 2025/11/6 10:19
 */
public class FieldHashSetWrapper implements Set<String> {

    private final Set<FieldCacheKey> fieldCacheSet = new HashSet<>();

    @Override
    public int size() {
        return this.fieldCacheSet.size();
    }

    @Override
    public boolean isEmpty() {
        return this.fieldCacheSet.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        //校验数据类型
        typeCheck(o);
        //将String包装成FieldCacheKey
        return this.fieldCacheSet.contains(FieldCacheKey.buildKey((String) o));
    }

    @Override
    public Iterator<String> iterator() {
        return this.fieldCacheSet.stream().map(FieldCacheKey::getCacheKey).iterator();
    }

    @Override
    public Object[] toArray() {
        return this.fieldCacheSet.stream().map(FieldCacheKey::getCacheKey).toArray();
    }

    @Override
    @Deprecated//暂不支持
    public <T> T[] toArray(T[] a) {
        return this.fieldCacheSet.toArray(a);
    }

    @Override
    public boolean add(String s) {
        return this.fieldCacheSet.add(FieldCacheKey.buildKey(s));
    }

    @Override
    public boolean remove(Object o) {
        //校验数据类型
        typeCheck(o);
        //将String包装成FieldCacheKey
        return this.fieldCacheSet.remove(FieldCacheKey.buildKey((String) o));
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            //校验数据类型
            typeCheck(o);
        }

        //将String包装成FieldCacheKey
        Set<FieldCacheKey> collect = c.stream().map(o -> FieldCacheKey.buildKey((String) o)).collect(Collectors.toSet());
        return this.fieldCacheSet.containsAll(collect);
    }

    @Override
    public boolean addAll(Collection<? extends String> c) {
        //将String包装成FieldCacheKey
        Set<FieldCacheKey> collect = c.stream().map(o -> FieldCacheKey.buildKey(o)).collect(Collectors.toSet());
        return this.fieldCacheSet.addAll(collect);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        for (Object s : c) {
            //校验数据类型
            typeCheck(s);
        }
        //将String包装成FieldCacheKey
        Set<FieldCacheKey> collect = c.stream().map(o -> FieldCacheKey.buildKey((String) o)).collect(Collectors.toSet());
        return this.fieldCacheSet.retainAll(collect);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        for (Object s : c) {
            //校验数据类型
            typeCheck(s);
        }
        //将String包装成FieldCacheKey
        Set<FieldCacheKey> collect = c.stream().map(o -> FieldCacheKey.buildKey((String) o)).collect(Collectors.toSet());
        return this.fieldCacheSet.retainAll(collect);
    }

    @Override
    public void clear() {
        this.fieldCacheSet.clear();
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
            throw new FieldException("FieldHashSetWrapper key 必须是String类型的");
        }
    }

}
