package com.sangsang.config;

import com.sangsang.cache.fielddefault.FieldDefaultInstanceCache;
import com.sangsang.domain.strategy.fielddefault.FieldDefaultStrategy;
import com.sangsang.interceptor.FieldDefaultInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author liutangqi
 * @date 2025/7/16 9:29
 */
@Configuration
public class FieldDefaultConfig {

    @Bean
    @ConditionalOnProperty(name = "sangsang.fieldDefault.enable", havingValue = "true")
    public FieldDefaultInstanceCache initFieldDefaultCache(@Autowired(required = false) List<FieldDefaultStrategy> fieldDefaultStrategies) {
        FieldDefaultInstanceCache fieldDefaultInstanceCache = new FieldDefaultInstanceCache();
        fieldDefaultInstanceCache.init(fieldDefaultStrategies);
        return fieldDefaultInstanceCache;
    }

    @Bean
    @ConditionalOnBean(FieldDefaultInstanceCache.class)
    public FieldDefaultInterceptor fieldDefaultInterceptor() {
        return new FieldDefaultInterceptor();
    }

}
