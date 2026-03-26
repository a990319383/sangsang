package com.sangsang.config;

import com.sangsang.cache.transformation.TransformationInstanceCache;
import com.sangsang.config.properties.SangSangProperties;
import com.sangsang.interceptor.TransformationInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * sql语法转换的配置
 *
 * @author liutangqi
 * @date 2025/5/26 13:55
 */
@Configuration
public class TransformationConfig {
    /**
     * 当前项目的sql语法转换器实例缓存
     *
     * @author liutangqi
     * @date 2025/5/26 15:53
     * @Param [sangSangProperties]
     **/
    @Bean
    @ConditionalOnProperty(name = "sangsang.transformation.patternType")
    public TransformationInstanceCache transformationInstanceCache(SangSangProperties sangSangProperties) {
        TransformationInstanceCache transformationInstanceCache = new TransformationInstanceCache();
        //初始化转换器实例
        transformationInstanceCache.init(sangSangProperties);
        return transformationInstanceCache;
    }

    /**
     * sql语法转换的拦截器
     *
     * @author liutangqi
     * @date 2025/5/26 15:51
     * @Param []
     **/
    @Bean
    @ConditionalOnBean(TransformationInstanceCache.class)
    public TransformationInterceptor transformationInterceptor() {
        return new TransformationInterceptor();
    }
}
