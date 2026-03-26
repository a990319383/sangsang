package com.sangsang.util;


import com.sangsang.domain.exception.FieldException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 反射相关的工具类
 *
 * @author liutangqi
 * @date 2023/6/30 11:15
 */
public class ReflectUtils {

    /**
     * 获取所有的字段
     *
     * @author liutangqi
     * @date 2023/6/30 13:17
     * @Param [cls]
     **/
    public static List<Field> getAllFields(Class cls) {
        List<Field> res = new ArrayList<>();
        //获取当前类所有字段
        res.addAll(Arrays.asList(cls.getDeclaredFields()));
        //获取所有父类字段
        Class superClass = cls.getSuperclass();
        while (superClass != null) {
            res.addAll(Arrays.asList(superClass.getDeclaredFields()));
            superClass = superClass.getSuperclass();
        }
        return res;
    }

    /**
     * 获取所有非static,非final修饰的字段
     *
     * @author liutangqi
     * @date 2024/7/9 11:03
     * @Param [cls]
     **/
    public static List<Field> getNotStaticFinalFields(Class cls) {
        //1.获取类的所有字段
        List<Field> allFields = ReflectUtils.getAllFields(cls);

        //2.过滤掉不属于实体类的字段，过滤掉static,final修饰的字段
        return allFields.stream()
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .filter(f -> !Modifier.isFinal(f.getModifiers()))
                .collect(Collectors.toList());
    }


    /**
     * 判断对象是否存在某个字段，存在的话就进行填充
     *
     * @author liutangqi
     * @date 2023/6/30 13:24
     * @Param [obj 需要填充的对象, filedName 字段名 , t 存在时进行填充的值]
     **/
    public static <T> void filFieldlIfExist(Object obj, String filedName, T t) {
        //获取所有的字段
        List<Field> allFields = getAllFields(obj.getClass());
        //筛选判断是否存在
        allFields.stream()
                .filter(f -> f.getName().equals(filedName) && f.getType().getTypeName().equals(t.getClass().getTypeName()))
                .forEach(f -> {
                    try {
                        f.setAccessible(true);
                        f.set(obj, t);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                });
    }

    /**
     * 获取对象指定字段名的值
     *
     * @author liutangqi
     * @date 2023/12/24 15:39
     * @Param [cls, fieldName]
     **/
    public static Object getFieldValue(Object obj, String fieldName) {
        //当前类查找
        Field field = Stream.of(obj.getClass().getDeclaredFields()).filter(f -> f.getName().equals(fieldName)).findAny().orElse(null);

        //当前类找不不到，去父类找
        Class superClass = obj.getClass().getSuperclass();
        while (field == null && superClass != null) {
            field = Stream.of(superClass.getDeclaredFields()).filter(f -> f.getName().equals(fieldName)).findAny().orElse(null);
            superClass = superClass.getSuperclass();
        }

        //没有此字段，则返回空
        if (field == null) {
            return null;
        }

        //设置可访问private的字段
        field.setAccessible(true);
        Object res = null;
        try {
            res = field.get(obj);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return res;
    }

    /**
     * 给obj对象的 fieldName字段 设置值为value
     *
     * @author liutangqi
     * @date 2024/9/22 14:03
     * @Param [obj, fieldName, value]
     **/
    public static void setFieldValue(Object obj, String fieldName, Object value) {
        //当前类查找
        Field field = Stream.of(obj.getClass().getDeclaredFields()).filter(f -> f.getName().equals(fieldName)).findAny().orElse(null);

        //当前类找不不到，去父类找
        Class superClass = obj.getClass().getSuperclass();
        while (field == null && superClass != null) {
            field = Stream.of(superClass.getDeclaredFields()).filter(f -> f.getName().equals(fieldName)).findAny().orElse(null);
            superClass = superClass.getSuperclass();
        }

        //没有此字段，则返回空
        if (field == null) {
            return;
        }

        //设置可访问private的字段
        field.setAccessible(true);
        Object res = null;
        try {
            field.set(obj, value);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * 根据对象的值，获取指定的obj对象里面值为这个的变量的变量名
     *
     * @author liutangqi
     * @date 2025/3/6 13:29
     * @Param [obj, fieldValue]
     **/
    public static String getFieldNameByValue(Object obj, Object fieldValue) throws IllegalAccessException {
        for (Field field : obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.get(obj).equals(fieldValue)) {
                return field.getName();
            }
        }
        return null;
    }

    /**
     * 将类的全限定名转换为Class对象
     *
     * @author liutangqi
     * @date 2025/12/1 15:20
     * @Param [className]
     **/
    public static Class forName(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new FieldException(String.format("此类全限定名无法转换为Class对象 %s  e:%s", className, e.getMessage()));
        }
    }
}
