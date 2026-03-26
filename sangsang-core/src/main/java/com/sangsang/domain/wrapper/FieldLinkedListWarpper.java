package com.sangsang.domain.wrapper;

import com.sangsang.domain.dto.FieldCacheKey;
import com.sangsang.domain.exception.FieldException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 装饰器模式将List<String> 进行一次包装，其中值进行大小写敏感处理，忽略关键字符的处理
 * 这里的采用的是LinkedList，保证数据的顺序性
 *
 * @author liutangqi
 * @date 2025/12/17 16:02
 */
public class FieldLinkedListWarpper implements List<String> {

    private final List<FieldCacheKey> linkedList = new LinkedList<>();

    @Override
    public int size() {
        return this.linkedList.size();
    }

    @Override
    public boolean isEmpty() {
        return this.linkedList.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        //空值校验
        if (o == null) {
            return false;
        }
        //类型校验
        typeCheck(o);
        //将String包装成FieldCacheKey
        return this.linkedList.contains(FieldCacheKey.buildKey((String) o));
    }

    @Override
    public Iterator<String> iterator() {
        return this.linkedList.stream().map(FieldCacheKey::getCacheKey).iterator();
    }

    @Override
    public Object[] toArray() {
        return this.linkedList.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return this.linkedList.toArray(a);
    }

    @Override
    public boolean add(String t) {
        return this.linkedList.add(FieldCacheKey.buildKey(t));
    }

    @Override
    public boolean remove(Object o) {
        //空值校验
        if (o == null) {
            return false;
        }
        //类型校验
        typeCheck(o);
        return this.linkedList.remove(FieldCacheKey.buildKey((String) o));
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object obj : c) {
            //空值校验
            if (obj == null) {
                return false;
            }
            //类型校验
            typeCheck(obj);
        }
        return this.linkedList.containsAll(c.stream().map(m -> FieldCacheKey.buildKey((String) m)).collect(Collectors.toCollection(LinkedList::new)));
    }

    @Override
    public boolean addAll(Collection<? extends String> c) {
        return this.linkedList.addAll(c.stream().map(FieldCacheKey::buildKey).collect(Collectors.toCollection(LinkedList::new)));
    }

    @Override
    public boolean addAll(int index, Collection<? extends String> c) {
        return this.linkedList.addAll(index, c.stream().map(FieldCacheKey::buildKey).collect(Collectors.toCollection(LinkedList::new)));
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        for (Object obj : c) {
            //空值校验
            if (obj == null) {
                return false;
            }
            //类型校验
            typeCheck(obj);
        }
        return this.linkedList.removeAll(c.stream().map(m -> FieldCacheKey.buildKey((String) m)).collect(Collectors.toCollection(LinkedList::new)));
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        for (Object obj : c) {
            //空值校验
            if (obj == null) {
                return false;
            }
            //类型校验
            typeCheck(obj);
        }
        return this.linkedList.retainAll(c.stream().map(m -> FieldCacheKey.buildKey((String) m)).collect(Collectors.toCollection(LinkedList::new)));
    }

    @Override
    public void clear() {
        this.linkedList.clear();
    }

    @Override
    public String get(int index) {
        return this.linkedList.get(index).getCacheKey();
    }

    @Override
    public String set(int index, String element) {
        return this.linkedList.set(index, FieldCacheKey.buildKey(element)).getCacheKey();
    }

    @Override
    public void add(int index, String element) {
        this.linkedList.add(index, FieldCacheKey.buildKey(element));
    }

    @Override
    public String remove(int index) {
        return this.linkedList.remove(index).getCacheKey();
    }

    @Override
    public int indexOf(Object o) {
        //空值校验
        if (o == null) {
            return -1;
        }
        //类型校验
        typeCheck(o);

        return this.linkedList.indexOf(FieldCacheKey.buildKey((String) o));
    }

    @Override
    public int lastIndexOf(Object o) {
        //空值校验
        if (o == null) {
            return -1;
        }
        //类型校验
        typeCheck(o);

        return this.linkedList.lastIndexOf(FieldCacheKey.buildKey((String) o));
    }

    @Override
    public ListIterator<String> listIterator() {
        return this.linkedList.stream().map(FieldCacheKey::getCacheKey).collect(Collectors.toCollection(LinkedList::new)).listIterator();
    }

    @Override
    public ListIterator<String> listIterator(int index) {
        return this.linkedList.stream().map(FieldCacheKey::getCacheKey).collect(Collectors.toCollection(LinkedList::new)).listIterator(index);
    }

    @Override
    public List<String> subList(int fromIndex, int toIndex) {
        return this.linkedList.subList(fromIndex, toIndex).stream().map(FieldCacheKey::getCacheKey).collect(Collectors.toCollection(LinkedList::new));
    }


    /**
     * 校验存值的类型是否是String
     *
     * @author liutangqi
     * @date 2025/12/17 16:13
     * @Param [obj]
     **/
    private void typeCheck(Object obj) {
        if (!(obj instanceof String)) {
            throw new FieldException("FieldLinkedListWarpper只能存储String类型");
        }
    }
}
