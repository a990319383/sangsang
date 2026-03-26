package com.sangsang.config;

import com.sangsang.cache.SqlParseCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.config.properties.SangSangProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/**
 * 核心配置类
 *
 * @author liutangqi
 * @date 2025/5/26 13:55
 */
@Configuration
@EnableConfigurationProperties({SangSangProperties.class})
public class SangSangConfig {
    /**
     * 初始化表结构字段信息到本地缓存
     *
     * @author liutangqi
     * @date 2024/9/19 15:22
     * @Param [encryptorProperties]
     **/
    @Bean
    public TableCache initTableCache(@Autowired(required = false) List<DataSource> dataSources,
                                     SangSangProperties sangSangProperties) {
        //1.初始化表结构字段信息到本地缓存
        TableCache.init(dataSources, sangSangProperties);

        //2.初始化jsqlparser解析缓存
        SqlParseCache.init(sangSangProperties);
        return new TableCache();
    }
}
