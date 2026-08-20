package com.sangsang.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记录当前mybtais的相关配置信息
 *
 * @author liutangqi
 * @date 2026/8/17 10:33
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SqlSessionFactoryConfig {
    /**
     * 是否开启下划线到驼峰的映射（这个配置是mybatis中查询的字段和接受值的java类字段之间的映射关系配置）
     * 对应配置：mybatis.configuration.map-underscore-to-camel-case
     * xml中sql的字段     java类变量名     配置值为false时能否映射   配置值为true时能够映射
     * ----驼峰             驼峰                 √                       √
     * ---下划线           下划线                 √                       ×
     * ---下划线            驼峰                  ×                       √
     * ----驼峰            下划线                 ×                       ×
     * PS: 当前版本实测，这些映射匹配时都是按大小写不敏感进行匹配的
     * PS：如果当前spring容器中没有成功读取到这个的配置项，默认值这里设置为true
     **/
    @Builder.Default
    private Boolean mapUnderscoreToCamelCase = Boolean.TRUE;


    public static final SqlSessionFactoryConfig DEFAULT = SqlSessionFactoryConfig.builder().build();
}
