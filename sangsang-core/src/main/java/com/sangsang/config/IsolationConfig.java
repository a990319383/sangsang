package com.sangsang.config;

import com.sangsang.aop.isolation.IsolationAspect;
import com.sangsang.cache.isolation.IsolationInstanceCache;
import com.sangsang.config.properties.SangSangProperties;
import com.sangsang.domain.strategy.isolation.DataIsolationStrategy;
import com.sangsang.interceptor.IsolationInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author liutangqi
 * @date 2025/6/13 13:16
 */
@Configuration
public class IsolationConfig {

    @Bean
    @ConditionalOnProperty(name = "sangsang.isolation.enable", havingValue = "true")
    public IsolationInstanceCache initIsolationCache(SangSangProperties sangSangProperties,
                                                     @Autowired(required = false) List<DataIsolationStrategy> dataIsolationStrategyList) throws Exception {
        IsolationInstanceCache isolationCache = new IsolationInstanceCache();
        isolationCache.init(sangSangProperties, dataIsolationStrategyList);
        return isolationCache;
    }

    @Bean
    @ConditionalOnBean(IsolationInstanceCache.class)
    public IsolationInterceptor isolationInterceptor() {
        return new IsolationInterceptor();
    }

    @Bean
    @ConditionalOnBean(IsolationInstanceCache.class)
    public IsolationAspect isolationAspect() {
        return new IsolationAspect();
    }
}
