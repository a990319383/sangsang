package com.sangsang.domain.dto;

import com.sangsang.domain.exception.FieldException;
import lombok.*;
import org.springframework.util.ClassUtils;

import java.io.Serializable;
import java.util.Objects;


/**
 * Class作为缓存key时，由于一些代理对象和类加载不同，可能导致存取值错误，所以，需要使用Class作为缓存key时，使用这个包一层
 * 框架产生的代理类，会获取到真实类，通过真实类的类全限定名去判断是否相同
 *
 * @author liutangqi
 * @date 2025/8/12 9:33
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class ClassCacheKey implements Serializable {
    /**
     * 非代理类的类名
     */
    private String className;
    /**
     * 类加载器
     * 暂时不管类加载器，只要类全限定名一致，就判定为同一个类，部分本地热部署等会导致类加载器不同
     */
//    private ClassLoader classLoader;


    /**
     * 私有化构造方法
     *
     * @author liutangqi
     * @date 2025/11/5 17:53
     * @Param [cacheKey]
     **/
    private ClassCacheKey(String className) {
        this.className = className;
    }


    /**
     * 构建缓存key
     *
     * @author liutangqi
     * @date 2025/12/01 15:06
     * @Param [clazz]
     **/
    public static ClassCacheKey buildKey(Class clazz) {
        //1.非空校验
        if (clazz == null) {
            throw new FieldException("ClassCacheKey 不能使用null的Class对象");
        }

        //2.如果是代理类的话，获取真实key
        Class<?> originalClass = ClassUtils.getUserClass(clazz);

        //3.构建返回对象
        return new ClassCacheKey(originalClass.getName());
    }


    /**
     * 判断两个Class对象是否相同
     * 去除掉框架代理和类加载器的影响
     *
     * @author liutangqi
     * @date 2025/12/1 15:16
     * @Param [clz1, clz2]
     **/
    public static boolean classEquals(Class clz1, Class clz2) {
        if (clz1 == null || clz2 == null) {
            return clz1 == clz2;
        }
        return Objects.equals(buildKey(clz1), buildKey(clz2));
    }

}

