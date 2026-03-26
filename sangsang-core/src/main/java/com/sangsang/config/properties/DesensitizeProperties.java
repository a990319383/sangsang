package com.sangsang.config.properties;

import lombok.Data;

/**
 * 脱敏相关的配置
 *
 * @author liutangqi
 * @date 2025/5/26 11:30
 */
@Data
public class DesensitizeProperties {
    /**
     * 是否开启脱敏功能
     */
    private boolean enable = false;
}
