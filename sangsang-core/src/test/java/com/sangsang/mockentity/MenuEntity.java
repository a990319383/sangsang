package com.sangsang.mockentity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;

/**
 * mock实体类，主要展示表结构，所以没有getter setter
 */
@TableName(value = "tb_menu")
public class MenuEntity extends BaseEntity {
    /**
     * 菜单名
     */
    @TableField(value = "menu_name")
    private String menuName;

    /**
     * 父级id,第一级是0
     */
    @TableField(value = "parent_id")
    private Long parentId;

    /**
     * 路径
     */
    @TableField(value = "path")
    @FieldEncryptor
    private String path;

    @TableField(exist = false)
    private String aaa;

}