package com.sangsang.domain.wrapper;

import cn.hutool.core.util.ObjectUtil;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.util.CollectionUtils;

import java.io.Serializable;
import java.util.*;

/**
 * 装饰器模式包装Map<String,Map<String,List<FieldInfoDto>>>
 * 用于保存sql中第几层的表字段信息
 * 自己包装一层主要解决sql字段的访问作用域的问题
 * 核心改动的是put()方法
 *
 * @author liutangqi
 * @date 2025/12/23 15:31
 */
public class LayerHashMapWrapper implements Map<Integer, Map<String, List<FieldInfoDto>>>, Serializable {

    /**
     * 存储当前具体的数据的Map
     */
    private final Map<Integer, Map<String, List<FieldInfoDto>>> layerMap = new HashMap<>();

    /**
     * 上游作用域的Map
     */
    private final Map<String, List<FieldInfoDto>> upstreamScopeMap = new FieldHashMapWrapper<>();

    /**
     * 存储当前层的表名
     * 当存在上游作用域的时候，我们会将上游作用域的表添加到每一层的解析结果中，当某些场景不需要上游作用域的信息，我们就需要依靠这个存储的信息来过滤
     * 注意：如果不使用这个表结构，单纯根据上游作用域的Map过滤剔除的话，在某些语法宽松的数据库中，上游作用域和下层是允许有同样的表别名的，这个时候会漏掉部分重复表
     */
    private final Map<Integer, Set<String>> layerTableMap = new HashMap<>();

    /**
     * 存在上游作用域的表字段的构造函数
     *
     * @author liutangqi
     * @date 2025/12/23 15:52
     * @Param [upstreamScopeMap]
     **/
    public LayerHashMapWrapper(Map<String, List<FieldInfoDto>> upstreamScopeMap) {
        if (CollectionUtils.isNotEmpty(upstreamScopeMap)) {
            this.upstreamScopeMap.putAll(upstreamScopeMap);
        }
    }

    /**
     * 无参构造
     *
     * @author liutangqi
     * @date 2025/12/23 15:52
     * @Param []
     **/
    public LayerHashMapWrapper() {
    }

    @Override
    public int size() {
        return this.layerMap.size();
    }

    @Override
    public boolean isEmpty() {
        return this.layerMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.layerMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return this.layerMap.containsValue(value);
    }

    @Override
    public Map<String, List<FieldInfoDto>> get(Object key) {
        return this.layerMap.get(key);
    }


    /**
     * 获取当前层的表字段，但是不包含上游作用域的表字段
     *
     * @author liutangqi
     * @date 2025/12/24 14:47
     * @Param [key]
     **/
    public Map<String, List<FieldInfoDto>> getExclusiveUpstreamScope(Integer key) {
        //1.上游作用域为空，直接返回结果
        if (CollectionUtils.isEmpty(this.upstreamScopeMap)) {
            return this.layerMap.get(key);
        }

        //2.上游作用域不为空，则需要将本层的数据做一次数据过滤，只保留本层的表字段信息
        // 获取当前层的所有表名，不包含上游作用域
        Set<String> curLayerTable = this.layerTableMap.get(key);
        FieldHashMapWrapper<List<FieldInfoDto>> result = new FieldHashMapWrapper<>();
        // 只保留curLayerTable中有的表 （注意：这里curLayerTable 真实类型其实也是FieldHashSetWrapper）
        for (Entry<String, List<FieldInfoDto>> entry : this.layerMap.getOrDefault(key, new FieldHashMapWrapper<>()).entrySet()) {
            if (curLayerTable.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }


    /**
     * put函数，当第一次put数据时，将上游作用域的Map中的数据添加到当前层Map中
     * 由于这个逻辑，所以返回值写死的null，没有像原HashMap一样，返回被顶替掉的旧值
     *
     * @author liutangqi
     * @date 2025/12/23 17:14
     * @Param [key, value]
     **/
    @Override
    public Map<String, List<FieldInfoDto>> put(Integer key, Map<String, List<FieldInfoDto>> value) {
        //1. 上游作用域不为空 && 第一次放这层数据 ，则将上游作用域的Map中的数据添加到当前Map中(注意：先放上游作用域的值，当前字段和上游字段重复时，当前的字段put时就可以把上游作用域的给替换掉，这个在某些语法校验不严格的数据库是这样的逻辑)
        if (CollectionUtils.isNotEmpty(upstreamScopeMap) && !this.layerMap.containsKey(key)) {
            //这里将上游作用域的数据深拷贝一份，避免对上游作用域的数据进行修改
            this.layerMap.put(key, ObjectUtil.cloneByStream(this.upstreamScopeMap));
        }

        //2.记录此层存放过的表
        CollectionUtils.putList(this.layerTableMap, key, new FieldHashSetWrapper(value.keySet()));

        //3. 之前不存在这个key，直接put
        Map<String, List<FieldInfoDto>> tableMap = this.layerMap.get(key);
        if (CollectionUtils.isEmpty(tableMap)) {
            this.layerMap.put(key, value);
            return null;
        }

        //4.之前存在这个key，则将值依次放进去
        for (Entry<String, List<FieldInfoDto>> entry : value.entrySet()) {
            tableMap.put(entry.getKey(), entry.getValue());
        }
        return null;
    }

    @Override
    public Map<String, List<FieldInfoDto>> remove(Object key) {
        return this.layerMap.remove(key);
    }

    @Override
    public void putAll(Map<? extends Integer, ? extends Map<String, List<FieldInfoDto>>> m) {
        //走单个put的逻辑
        for (Entry<? extends Integer, ? extends Map<String, List<FieldInfoDto>>> entry : m.entrySet()) {
            this.layerMap.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        this.layerMap.clear();
    }

    @Override
    public Set<Integer> keySet() {
        return this.layerMap.keySet();
    }

    @Override
    public Collection<Map<String, List<FieldInfoDto>>> values() {
        return this.layerMap.values();
    }

    @Override
    public Set<Entry<Integer, Map<String, List<FieldInfoDto>>>> entrySet() {
        return this.layerMap.entrySet();
    }
}
