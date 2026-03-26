package com.sangsang.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生成实体类需要的参数
 *
 * @author liutangqi
 * @date 2025/8/19 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GenerateDto {
    /**
     * 数据库的地址
     * 栗如：jdbc:mysql://127.0.0.1:3306/your_database
     */
    private String url;
    /**
     * 数据库账号，注意权限
     * 确保拥有查看表结构的权限
     */
    private String username;
    /**
     * 数据库密码
     */
    private String password;
    /**
     * 文件输出目录
     */
    private String outputDir;
    /**
     * 基础包名
     * com.example.entity
     */
    private String packageName;

    //-----------------------------分割线，上面是必填字段，下面是根据情况选填字段----------------------------------
    /**
     * 目录
     * mysql一般传数据库名称
     * Oracle一般传null
     */
    String catalog;
    /**
     * 模式
     * Oracle一般传用户名/模式名（大写）
     * mysql一般传null
     */
    String schemaPattern;
    /**
     * 表名过滤规则
     * % 匹配任意多个字符
     * _ 匹配单个字符
     * null的话表示不限制
     */
    String tableNamePattern;
}
