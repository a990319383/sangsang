package com.sangsang.config.properties;

import com.sangsang.domain.constants.NumberConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;


/**
 * @author liutangqi
 * @date 2025/5/26 11:28
 */
@Data
@ConfigurationProperties(prefix = "field")
public class FieldProperties {
    /**
     * 扫描的实体类的包路径
     * 如需使用数据库加解密功能，sql语法转换功能，需要配置
     */
    private List<String> scanEntityPackage = new ArrayList<>();
    /**
     * sql语法解析的LRU缓存长度
     * 默认500
     */
    private Integer lruCapacity = NumberConstant.FIVE_HUNDRED;

    /**
     * 是否自动补齐当前库的表结构信息到本地缓存，只会缓存需要用到的表结构
     * 默认值是补齐，若遇数据库没权限则关闭此配置项
     * 如果项目是mybatis项目，本身没有实体类的话，开启此配置，实体类只保留需要标注的字段即可
     */
    private boolean autoFill = true;

    /**
     * 是否区分大小写敏感
     * 默认是大小写不敏感
     */
    private boolean caseSensitive = false;

    /**
     * 数据库标识符的引用符，比如mysql是 ` 达梦数据库是 "
     * 默认不配置的话，会从第一个dataSource中获取
     * 目前暂不支持不同类型数据库的多数据源项目
     */
    private String identifierQuote;

    /**
     * 加解密相关的配置
     **/
    private EncryptorProperties encryptor;
    /**
     * 脱敏相关的配置
     **/
    private DesensitizeProperties desensitize;
    /**
     * sql语法转换相关的配置
     */
    private TransformationProperties transformation;

    /**
     * 数据隔离的相关配置
     **/
    private IsolationProperties isolation;

    /**
     * 字段设置默认值相关配置
     */
    private FieldDefaultProperties fieldDefault;
}
