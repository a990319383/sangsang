package com.sangsang.domain.annos.desensitize;

import com.sangsang.domain.strategy.desensitize.DesensitizeStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 脱敏的注解，标注在响应的实体类的字段上
 * 使用方法：
 * 场景1：返回值是实体类 ---> 将@FieldDesensitize 标注在需要脱敏处理的字段上面，指定具体的脱敏算法即可 value= ? extends DesensitizeInterface.class
 * 场景2：返回值是Map    ---> 将@MapperDesensitize 标注在mapper上，指定返回值的脱敏算法和具体Map的key
 * 场景3：返回值是String ---> 将@MapperDesensitize 标注在mapper上，指定返回值的脱敏算法
 * 备注：上面的实体类包含List<实体类> String也包含List<String>
 *
 * @author liutangqi
 * @date 2025/4/7 17:23
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldDesensitize {
    /**
     * 具体的脱敏算法
     **/
    Class<? extends DesensitizeStrategy> value();

    /**
     * 需要进行脱敏的字段名（只有标注在Mapper上面，并且返回值是Map的时候才需要）
     **/
    String fieldName() default "";
}
