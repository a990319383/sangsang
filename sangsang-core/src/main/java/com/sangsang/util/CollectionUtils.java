package com.sangsang.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.wrapper.FieldHashSetWrapper;

import java.util.*;

/**
 * @author liutangqi
 * @date 2024/9/9 15:53
 */
public class CollectionUtils {
    /**
     * 空集合
     */
    public static final List EMPTY_LIST = Collections.emptyList();
    public static final Set EMPTY_SET = Collections.emptySet();
    public static final Map EMPTY_MAP = Collections.emptyMap();

    /**
     * 校验集合是否为空
     *
     * @param coll 入参
     * @return boolean
     */
    public static boolean isEmpty(Collection<?> coll) {
        return (coll == null || coll.isEmpty());
    }

    /**
     * 校验集合是否不为空
     *
     * @param coll 入参
     * @return boolean
     */
    public static boolean isNotEmpty(Collection<?> coll) {
        return !isEmpty(coll);
    }

    /**
     * 校验集合是否为空
     *
     * @param map 入参
     * @return boolean
     * @copy org.springframework.util.CollectionUtils
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return (map == null || map.isEmpty());
    }

    /**
     * 校验集合是否不为空
     *
     * @param map 入参
     * @return boolean
     * @copy org.springframework.util.CollectionUtils
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    /**
     * 判断两个List是否相等
     *
     * @author liutangqi
     * @date 2025/3/4 18:06
     * @Param [listA, listB]
     **/
    public static boolean equals(List listA, List listB) {
        return listA.size() == listB.size() && CollUtil.containsAll(listA, listB);
    }

    /**
     * 判断两个Map是否相等（只判断一层）
     *
     * @author liutangqi
     * @date 2025/3/4 18:18
     * @Param [mapA, mapB]
     **/
    public static boolean equals(Map mapA, Map mapB) {
        Set keySetA = mapA.keySet();
        Set keySetB = mapB.keySet();
        if (keySetA.size() != keySetB.size()) {
            return false;
        }

        for (Object key : keySetA) {
            if (!Objects.equals(mapA.get(key), mapB.get(key))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 在原有的List中，每个间隔插入分隔符
     *
     * @author liutangqi
     * @date 2025/5/30 16:44
     * @Param [lists, separator]
     **/
    public static <T> List<T> join(List<T> lists, T separator) {
        List<T> res = new ArrayList<>();
        for (int i = 0; i < lists.size(); i++) {
            res.add(lists.get(i));
            if (i != lists.size() - 1) {
                res.add(separator);
            }
        }
        return res;
    }

    /**
     * 从Map中获取值，获取成功后，再将该值给移除掉
     *
     * @author liutangqi
     * @date 2025/7/18 10:58
     * @Param [map, key]
     **/
    public static <K, V> V getAndRemove(Map<K, V> map, K key) {
        //1.先获取值
        V res = map.get(key);

        //2.判断Map中是否包含此key，包含就移除（注意：这里不能判断上面get的值是否为null来作为移除依据，因为Map中可以存null值作为value）
        if (map.containsKey(key)) {
            map.remove(key);
        }
        return res;
    }


    /**
     * 往Map<K,Set<V>> 中添加元素
     *
     * @param map        往这个map中存放元素
     * @param key
     * @param value
     * @param defaultSet 当map中这个key不存在时，使用这个默认集合存放value
     * @author liutangqi
     * @date 2025/12/24 14:58
     **/
    public static <K, V> void putList(Map<K, Set<V>> map, K key, V value, Set<V> defaultSet) {
        Set<V> list = map.get(key);
        if (list == null) {
            list = defaultSet;
            map.put(key, list);
        }
        list.add(value);
    }

    /**
     * Map<K, Set<V>> map 中添加集合元素，当Map中对应的key的集合不存在时，则使用defaultSet来存放这些value
     *
     * @author liutangqi
     * @date 2025/12/24 14:58
     * @Param [map, key, value]
     **/
    public static <K, V> void putList(Map<K, Set<V>> map, K key, Collection<V> values, Set<V> defaultSet) {
        if (CollectionUtils.isNotEmpty(values)) {
            for (V value : values) {
                putList(map, key, value, defaultSet);
            }
        }
    }


    /**
     * 往Map<K,Map<F,V>> 中添加元素
     *
     * @param map        往这个map中存放元素
     * @param key
     * @param field
     * @param value
     * @param defaultMap 当map中这个key不存在时，使用这个默认集合存放value
     * @author liutangqi
     * @author liutangqi
     * @date 2026/8/26 14:54
     * @Param [map, key, field, value, defaultMap]
     **/
    public static <K, F, V> void putMap(Map<K, Map<F, V>> map, K key, F field, V value, Map<F, V> defaultMap) {
        Map<F, V> fMap = map.get(key);
        if (fMap == null) {
            fMap = defaultMap;
            map.put(key, fMap);
        }
        fMap.put(field, value);
    }

    /**
     * pojo模式校验处理后的json数据是否相等
     * 备注：处理JsonObject
     *
     * @author liutangqi
     * @date 2025/11/12 17:13
     * @Param [sql1, sql2]
     **/
    public static boolean jsonObjectEquals(JSONObject jsonObject1, JSONObject jsonObject2) {
        Set<Map.Entry<String, Object>> entries1 = jsonObject1.entrySet();
        Set<Map.Entry<String, Object>> entries2 = jsonObject2.entrySet();
        //个数不等肯定不同
        if (entries1.size() != entries2.size()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : entries1) {
            Object obj1 = entry.getValue();
            Object obj2 = jsonObject2.get(entry.getKey());
            //类型不同，肯定不同
            if (!obj1.getClass().equals(obj2.getClass())) {
                return false;
            }
            //都是字符串类型，用当前配置是否大小写敏感那套规则比较
            else if (obj1 instanceof String) {
                //一处不同则不同
                if (!StringUtils.fieldEquals((String) obj1, (String) obj2)) {
                    return false;
                }
            }
            //都是JSONObject，则递归比较
            else if (obj1 instanceof JSONObject) {
                //一处不同则不同
                if (!jsonObjectEquals((JSONObject) obj1, (JSONObject) obj2)) {
                    return false;
                }
            }
            //都是JSONArray，则递归比较
            else if (obj1 instanceof JSONArray) {
                if (!jsonArrayEquals((JSONArray) obj1, (JSONArray) obj2)) {
                    return false;
                }
            }
            //其它类型根据equals比较
            else if (!Objects.equals(obj1, obj2)) {
                return false;
            }
        }
        return true;
    }


    /**
     * pojo模式校验处理后的json数据是否相等
     * 备注：处理JsonArray
     *
     * @author liutangqi
     * @date 2025/11/12 17:13
     * @Param [sql1, sql2]
     **/
    public static boolean jsonArrayEquals(JSONArray jsonArray1, JSONArray jsonArray2, String... ignoreKey) {
        //长度不同，肯定不同
        if (jsonArray1.size() != jsonArray2.size()) {
            return false;
        }

        //按同一规则排个序(根据项目配置转换大小写，和去除关键字标识符)
        jsonArray1.sort(Comparator.comparing(CollectionUtils::fieldComparingRule));
        jsonArray2.sort(Comparator.comparing(CollectionUtils::fieldComparingRule));

        //依次判断每一个
        for (int i = 0; i < jsonArray1.size(); i++) {
            if (!jsonObjectEquals(jsonArray1.getJSONObject(i), jsonArray2.getJSONObject(i))) {
                return false;
            }
        }
        //都相同，就相同
        return true;
    }

    /**
     * 判断结果是否正确时的排序规则
     * 根据当前项目开启的大小写敏感和关键字标识符转换成对应的字符串
     *
     * @author liutangqi
     * @date 2025/11/13 13:59
     * @Param [obj]
     **/
    private static String fieldComparingRule(Object obj) {
        String comparingStr = obj.toString();
        if (!TableCache.getCurConfig().isCaseSensitive()) {
            comparingStr = comparingStr.toLowerCase();
        }
        for (String identifierQuote : TableCache.getCurConfig().getIdentifierQuote()) {
            comparingStr = comparingStr.replaceAll(identifierQuote, SymbolConstant.BLANK);
        }
        return comparingStr;
    }

}
