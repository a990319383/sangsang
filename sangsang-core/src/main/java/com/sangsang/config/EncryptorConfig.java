package com.sangsang.config;

import com.sangsang.cache.encryptor.EncryptorInstanceCache;
import com.sangsang.config.properties.SangSangProperties;
import com.sangsang.domain.constants.EncryptorPatternTypeConstant;
import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;
import com.sangsang.encryptor.db.DefaultDBFieldEncryptorPattern;
import com.sangsang.encryptor.pojo.DefaultPoJoFieldEncryptorPattern;
import com.sangsang.interceptor.DBFieldEncryptorInterceptor;
import com.sangsang.interceptor.PoJoParamEncrtptorInterceptor;
import com.sangsang.interceptor.PoJoResultEncrtptorInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 加解密的配置
 *
 * @author liutangqi
 * @date 2025/5/26 13:47
 */
@Configuration
public class EncryptorConfig {

    /**
     * 缓存当前项目配置的加密算法
     * 可能是db模式的也可能是pojo模式的
     *
     * @author liutangqi
     * @date 2024/9/19 14:34
     **/
    @Bean
    @ConditionalOnProperty(name = "sangsang.encryptor.patternType")
    public EncryptorInstanceCache encryptorCache(List<FieldEncryptorStrategy> strategies) {
        EncryptorInstanceCache encryptorCache = new EncryptorInstanceCache();
        encryptorCache.init(strategies);
        return encryptorCache;
    }

    /**
     * 注册pojo模式入参加解密的拦截器
     *
     * @author liutangqi
     * @date 2024/9/10 14:49
     * @Param [sqlSessionFactory]
     **/
    @Bean
    @ConditionalOnProperty(name = "sangsang.encryptor.patternType", havingValue = EncryptorPatternTypeConstant.POJO)
    public PoJoParamEncrtptorInterceptor pojoParamInterceptor() {
        return new PoJoParamEncrtptorInterceptor();
    }

    /**
     * 注册pojo模式响应加解密的拦截器
     *
     * @author liutangqi
     * @date 2024/9/10 14:49
     * @Param [sqlSessionFactory]
     **/
    @Bean
    @ConditionalOnProperty(name = "sangsang.encryptor.patternType", havingValue = EncryptorPatternTypeConstant.POJO)
    public PoJoResultEncrtptorInterceptor pojoResultInterceptor() {
        return new PoJoResultEncrtptorInterceptor();
    }


    /**
     * 默认的pojo加解密算法
     *
     * @author liutangqi
     * @date 2024/9/19 13:40
     * @Param []
     **/
    @Bean
    @ConditionalOnMissingBean(FieldEncryptorStrategy.class)
    @ConditionalOnProperty(name = "sangsang.encryptor.patternType", havingValue = EncryptorPatternTypeConstant.POJO)
    public FieldEncryptorStrategy defaultPoJoFieldEncryptorPattern(SangSangProperties sangSangProperties) {
        return new DefaultPoJoFieldEncryptorPattern(sangSangProperties);
    }


    /**
     * 注册db模式加解密的拦截器
     *
     * @author liutangqi
     * @date 2024/9/10 14:49
     * @Param [sqlSessionFactory]
     **/
    @Bean
    @ConditionalOnProperty(name = "sangsang.encryptor.patternType", havingValue = EncryptorPatternTypeConstant.DB)
    public DBFieldEncryptorInterceptor dbInterceptor() {
        return new DBFieldEncryptorInterceptor();
    }

    /**
     * 默认的db模式下的加解密算法
     *
     * @author liutangqi
     * @date 2024/9/19 13:45
     * @Param [encryptorProperties]
     **/
    @Bean
    @ConditionalOnMissingBean(FieldEncryptorStrategy.class)
    @ConditionalOnProperty(name = "sangsang.encryptor.patternType", havingValue = EncryptorPatternTypeConstant.DB)
    public FieldEncryptorStrategy defaultDBFieldEncryptorPattern(SangSangProperties sangSangProperties) {
        return new DefaultDBFieldEncryptorPattern(sangSangProperties);
    }

}
