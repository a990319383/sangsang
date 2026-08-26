package com.sangsang.util;

import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.exception.FieldException;
import com.sangsang.domain.wrapper.ClassHashMapWrapper;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 定制化深克隆，为了性能考虑，采用最质朴的getter setter
 *
 * @author liutangqi
 * @date 2026/8/24 18:12
 */
public class DeepCloneUtil {

    public static class MapClone {
        /**
         * 缓存当前的构造器
         **/
        private static final Map<Class, Constructor<? extends Map>> CONSTRUCTOR_MAP = new ClassHashMapWrapper();

        /**
         * Map<Integer, Map<String, List<FieldInfoDto>>>类型的定制化深克隆
         *
         * @author liutangqi
         * @date 2026/8/25 14:10
         * @Param [map]
         **/
        public static Map<Integer, Map<String, List<FieldInfoDto>>> cloneIntKey(Map<Integer, Map<String, List<FieldInfoDto>>> map) {
            //0.空值处理
            if (map == null) {
                return map;
            }
            //1.从缓存获取构造器
            Constructor<? extends Map> constructor = getConstructor(map.getClass());

            //2.通过构造器实例化对象
            Map<Integer, Map<String, List<FieldInfoDto>>> instanceMap = newInstance(map.getClass(), constructor);

            //3.遍历map，将每一个值都克隆一份，然后放入新的map中
            for (Map.Entry<Integer, Map<String, List<FieldInfoDto>>> entry : map.entrySet()) {
                instanceMap.put(entry.getKey(), cloneStrKey(entry.getValue()));
            }

            return instanceMap;
        }

        /**
         * Map<String, List<FieldInfoDto>>类型的定制化深克隆
         *
         * @author liutangqi
         * @date 2026/8/25 14:10
         * @Param [map]
         **/
        public static Map<String, List<FieldInfoDto>> cloneStrKey(Map<String, List<FieldInfoDto>> map) {
            //0.空值处理
            if (map == null) {
                return map;
            }
            //1.从缓存获取构造器
            Constructor<? extends Map> constructor = getConstructor(map.getClass());

            //2.通过构造器实例化对象
            Map<String, List<FieldInfoDto>> instanceMap = newInstance(map.getClass(), constructor);

            //3.遍历map，将每一个值都克隆一份，然后放入新的map中
            for (Map.Entry<String, List<FieldInfoDto>> entry : map.entrySet()) {
                instanceMap.put(entry.getKey(), cloneFieldInfoDtoList(entry.getValue()));
            }
            return instanceMap;
        }

        /**
         * 通过缓存获取构造器
         *
         * @author liutangqi
         * @date 2026/8/25 15:15
         * @Param [clazz]
         **/
        private static Constructor<? extends Map> getConstructor(Class<? extends Map> clazz) {
            Constructor<? extends Map> cacheConstructor = CONSTRUCTOR_MAP.get(clazz);
            if (cacheConstructor != null) {
                return cacheConstructor;
            }
            Constructor<? extends Map> constructor = null;
            try {
                constructor = clazz.getConstructor();
            } catch (NoSuchMethodException e) {
                throw new FieldException(String.format("反射获取构造器异常 %s %s", clazz.getName(), e.getMessage()));
            }
            CONSTRUCTOR_MAP.put(clazz, constructor);
            return constructor;
        }

        /**
         * 通过cacheConstructor实例化对象
         *
         * @author liutangqi
         * @date 2026/8/25 15:27
         * @Param [cls, cacheConstructor]
         **/
        private static <K, V> Map<K, V> newInstance(Class cls, Constructor<? extends Map> cacheConstructor) {
            Map<K, V> instanceMap = null;
            try {
                instanceMap = cacheConstructor.newInstance();
            } catch (Exception e) {
                throw new FieldException(String.format("通过无参构造器实例化对象异常 %s %s", cls.getName(), e.getMessage()));
            }
            return instanceMap;
        }

        /**
         * 深克隆List<FieldInfoDto>
         *
         * @author liutangqi
         * @date 2026/8/25 14:39
         * @Param [list]
         **/
        private static List<FieldInfoDto> cloneFieldInfoDtoList(List<FieldInfoDto> list) {
            if (list == null) {
                return list;
            }
            List<FieldInfoDto> resList = new ArrayList<>(list.size());
            for (FieldInfoDto fieldInfoDto : list) {
                resList.add(new FieldInfoDto(
                        fieldInfoDto.getColumnName(),
                        fieldInfoDto.getSourceColumn(),
                        fieldInfoDto.getSourceTableName(),
                        fieldInfoDto.isFromSourceTable(),
                        fieldInfoDto.isRowNumber()
                ));
            }
            return resList;
        }
    }
}
