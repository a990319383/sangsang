package com.sangsang.mockentity;


import com.baomidou.mybatisplus.annotation.TableId;
import com.sangsang.domain.annos.fielddefault.FieldDefault;
import com.sangsang.strategy.TestCreateFieldDefaultStrategy;
import com.sangsang.strategy.TestUpdateFieldDefaultStrategy;

import java.time.LocalDateTime;

/**
 * mock实体类，主要展示表结构，所以没有getter setter
 *
 * @author liutangqi
 * @date 2023/6/21 17:27
 */
public class BaseEntity {
    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 创建时间
     */
    @FieldDefault(TestCreateFieldDefaultStrategy.class)
    private LocalDateTime createTime;
    /**
     * 修改时间
     */
    @FieldDefault(value = TestUpdateFieldDefaultStrategy.class, mandatoryOverride = true)
    private LocalDateTime updateTime;

    public static final String CREATE_TIME = "createTime";
    public static final String UPDATE_TIME = "updateTime";

    public static final String DB_CREATE_TIME = "create_time";
    public static final String DB_UPDATE_TIME = "update_time";
}
