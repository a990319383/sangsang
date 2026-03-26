package com.sangsang.config;

import com.sangsang.cache.desensitize.DesensitizeInstanceCache;
import com.sangsang.domain.strategy.desensitize.DesensitizeStrategy;
import com.sangsang.interceptor.FieldDesensitizeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 脱敏的注册配置
 *
 * @author liutangqi
 * @date 2025/5/26 13:45
 */
@Configuration
public class DesensitizeConfig {
    /**
     * 脱敏策略实例初始化
     *
     * @author liutangqi
     * @date 2025/7/16 17:19
     * @Param [strategies]
     **/
    @Bean
    @ConditionalOnProperty(name = "sangsang.desensitize.enable", havingValue = "true")
    public DesensitizeInstanceCache desensitizeInstanceCache(@Autowired(required = false) List<DesensitizeStrategy> strategies) {
        DesensitizeInstanceCache instanceCache = new DesensitizeInstanceCache();
        instanceCache.init(strategies);
        return instanceCache;
    }

    /**
     * 注册开启脱敏功能的拦截器
     *
     * @author liutangqi
     * @date 2025/4/8 10:48
     * @Param [poJoResultEncrtptorInterceptor, dbFieldEncryptorInterceptor]
     **/
    @Bean
    @ConditionalOnBean(DesensitizeInstanceCache.class)
    public FieldDesensitizeInterceptor fieldDesensitizeInterceptor() {
        return new FieldDesensitizeInterceptor();
    }
}
