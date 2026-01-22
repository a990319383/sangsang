package com.sangsang.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前可以从DataSource中读取的一些配置信息
 *
 * @author liutangqi
 * @date 2026/1/20 10:13
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DataSourceConfig {
    /**
     * 当前数据库的大版本号
     * 比如：mysql5.7的话这个值是5
     */
    private Integer databaseMajorVersion;
    /**
     * 当前数据库的小版本号
     * 比如：mysql5.7的话这个值是7
     */
    private Integer databaseMinorVersion;

    public static final DataSourceConfig DEFAULT = DataSourceConfig.builder().build();
}
