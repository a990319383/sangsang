package com.sangsang.config.properties;

import com.sangsang.domain.constants.EncryptorPatternTypeConstant;
import com.sangsang.domain.constants.SymbolConstant;
import lombok.Data;

/**
 * @author liutangqi
 * @date 2024/4/8 15:22
 */
@Data
public class EncryptorProperties {
    /**
     * 秘钥，下面是默认值
     */
    private String secretKey = SymbolConstant.DEFAULT_SECRET_KEY;

    /**
     * 加解密的模式类型
     *
     * @see EncryptorPatternTypeConstant
     */
    private String patternType;

    /**
     * pojo模式做字段映射的时候会使用到这个配置值
     * 如果没有配置的话，拦截器中会默认从 Configuration 读取这个配置项的值，如果没有读取到，默认值为true
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
    private Boolean mapUnderscoreToCamelCase;
}
