package com.sangsang.domain.context;

import com.sangsang.domain.constants.FieldConstant;

import java.util.*;

/**
 * @author liutangqi
 * @date 2026/1/8 17:41
 */
public class TfParameterMappingHolder {

    /**
     * 存储当前执行sql的 #{}的值
     * key: FieldConstant.PLACEHOLDER加自增序列
     * value: #{}的参数值
     */
    private static ThreadLocal<Map<String, Object>> PARAMETER_MAPPING_HOLDER = new ThreadLocal();

    /**
     * 记录原先sql中有的#{} ，但是转换后，#{}给替换掉了
     * 比如sql中本来是  FieldConstant.PLACEHOLDER加自增序列，但是经过语法转换后，这个占位符消失了，此时就需要维护在这个里面
     * 注意：这里维护的是 FieldConstant.PLACEHOLDER后的 序列，取的时候方便直接取
     **/
    private static ThreadLocal<Set<Integer>> REMOVE_PARAMETER_MAPPING = new ThreadLocal();


    /**
     * 设置当前 sql的 #{}的值
     *
     * @param placeholder FieldConstant.PLACEHOLDER加自增序列
     * @param paramValue  #{}具体的值
     * @author liutangqi
     * @date 2026/1/9 13:21
     **/
    public static void setParameterMapping(String placeholder, Object paramValue) {
        Map<String, Object> paramMap = PARAMETER_MAPPING_HOLDER.get();
        if (paramMap == null) {
            paramMap = new HashMap<>();
            PARAMETER_MAPPING_HOLDER.set(paramMap);
        }

        paramMap.put(placeholder, paramValue);
    }

    /**
     * 获取当前  sql的 #{}的值
     *
     * @param placeholder FieldConstant.PLACEHOLDER加自增序列
     * @author liutangqi
     * @date 2026/1/9 13:22
     **/
    public static Object getParameterMapping(String placeholder) {
        Map<String, Object> paramMap = PARAMETER_MAPPING_HOLDER.get();
        if (paramMap == null) {
            return null;
        }
        return paramMap.get(placeholder);
    }

    /**
     * 清空当前  sql的 #{}的值
     * 注意：请记得finally清除
     *
     * @author liutangqi
     * @date 2026/1/9 13:24
     * @Param []
     **/
    public static void clearParameterMapping() {
        PARAMETER_MAPPING_HOLDER.remove();
    }


    /**
     * 记录哪些#{}原来有，但是sql改写后 ，被删除了的占位符
     *
     * @param placeholder FieldConstant.PLACEHOLDER加自增序列
     * @author liutangqi
     * @date 2026/1/9 13:28
     **/
    public static void recordRemoveParameterMapping(String placeholder) {
        Set<Integer> placeholders = REMOVE_PARAMETER_MAPPING.get();
        if (placeholders == null) {
            placeholders = new HashSet<>();
            REMOVE_PARAMETER_MAPPING.set(placeholders);
        }

        placeholders.add(Integer.parseInt(placeholder.substring(FieldConstant.PLACEHOLDER.length())));
    }


    /**
     * 获取当前经过sql改写后 ，被删除了的占位符
     *
     * @author liutangqi
     * @date 2026/1/9 13:32
     * @Param []
     **/
    public static Set<Integer> getRemoveParameterMapping() {
        return Optional.ofNullable(REMOVE_PARAMETER_MAPPING.get()).orElse(Collections.EMPTY_SET);
    }

    /**
     * 清除当前记录的值， 注意：请记得finally清除
     *
     * @author liutangqi
     * @date 2026/1/9 13:32
     * @Param []
     **/
    public static void clearRemoveParameterMapping() {
        REMOVE_PARAMETER_MAPPING.remove();
    }


}
