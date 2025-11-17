package com.sangsang.util;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.NamingCase;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.db.meta.Column;
import cn.hutool.db.meta.MetaUtil;
import cn.hutool.db.meta.Table;
import com.sangsang.domain.dto.GenerateDto;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * mysql实体类生成工具类
 *
 * @author liutangqi
 * @date 2025/8/18 18:00
 */
@Slf4j
public class EntityGenerateUtil {

    /**
     * 生成实体类到指定目录
     *
     * @author liutangqi
     * @date 2025/8/19 13:13
     * @Param [dto]
     **/
    public static void generateEntity(GenerateDto dto) {
        // 创建数据源
        DataSource dataSource = new SimpleDataSource(dto.getUrl(), dto.getUsername(), dto.getPassword());

        try {
            // 创建输出目录(自动递归创建)
            FileUtil.mkdir(dto.getOutputDir());

            // 获取所有表名
            List<String> tableNames = getTableNames(dataSource, dto);

            // 为每个表生成实体类
            List<CompletableFuture> futures = new ArrayList<>();
            for (String tableName : tableNames) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    generateEntityClass(dataSource, tableName, dto);
                });
                futures.add(future);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();

            log.info("实体类生成完成，共生成{}个实体类", tableNames.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 获取所有表名（兼容Hutool 5.8.11）
     */
    public static List<String> getTableNames(DataSource dataSource, GenerateDto dto) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(dto.getCatalog(), dto.getSchemaPattern(), dto.getTableNamePattern(), new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
        }
        return tables;
    }

    /**
     * 生成实体类
     */
    private static void generateEntityClass(DataSource dataSource, String tableName, GenerateDto dto){
        // 获取表元数据（使用Hutool的MetaUtil）
        Table table = MetaUtil.getTableMeta(dataSource, tableName);

        // 准备类内容
        StringBuilder classContent = new StringBuilder();
        String className = NamingCase.toPascalCase(tableName); // 表名转大驼峰

        // 包声明
        classContent.append("package ").append(dto.getPackageName()).append(";\n\n");

        // 导入语句
        classContent.append("import com.baomidou.mybatisplus.annotation.*;\n");
        classContent.append("import lombok.Data;\n");
        classContent.append("import java.time.LocalDateTime;\n");
        classContent.append("import java.math.BigDecimal;\n\n");

        // 类注释
        classContent.append("/**\n");
        classContent.append(" * ").append(tableName).append(" 实体类\n");
        classContent.append(" * 【field-encryptor】 https://gitee.com/tired-of-the-water/field-encryptor  \n");
        classContent.append(" * 自动生成于").append(LocalDateTime.now()).append("\n");
        classContent.append(" */\n");

        // @Data注解
        classContent.append("@Data\n");

        // @TableName注解
        classContent.append("@TableName(\"").append(tableName).append("\")\n");

        // 类定义
        classContent.append("public class ").append(className).append(" {\n\n");

        //拼接字段
        for (Column column : table.getColumns()) {
            String columnName = column.getName();
            String fieldName = NamingCase.toCamelCase(columnName); // 字段名转小驼峰
            String typeName = column.getTypeName();
            Long columnSize = column.getSize();
            String remarks = column.getComment();
            // 字段注释
            classContent.append("    /**\n");
            classContent.append("     * ").append(StrUtil.isNotBlank(remarks) ? remarks : columnName).append("\n");
            classContent.append("     */\n");
            // 特殊字段处理
            if (column.isPk()) {
                classContent.append("    @TableId(type = IdType.AUTO)\n");
            } else {
                // 普通字段，驼峰转换后和原字段一致，也加上注解
//                if (!columnName.equalsIgnoreCase(fieldName)) {
                classContent.append("    @TableField(\"").append(columnName).append("\")\n");
//                }
            }
            // 字段定义
            classContent.append("    private ").append(DatabaseTypeMapper.getJavaType(typeName, columnSize)).append(" ")
                    .append(fieldName).append(";\n\n");
        }


        classContent.append("}");

        // 写入文件(使用Hutool的FileUtil)
        String filePath = dto.getOutputDir() + File.separator + className + ".java";
        FileUtil.writeUtf8String(classContent.toString(), filePath);

        log.info("生成实体类: {}.java", className);
    }

    //-------------------------------------------------分割线--------------------------------------------------------

    /**
     * 存储数据库类型到Java类型的映射关系
     *
     * @author liutangqi
     * @date 2025/8/19 18:10
     * @Param
     **/
    public static class DatabaseTypeMapper {

        // 数据库类型到Java类型的映射表
        private static final Map<String, String> TYPE_MAPPING = new HashMap<>();

        // 数据库特定类型前缀映射
        private static final Map<String, String> PREFIX_MAPPING = new HashMap<>();

        static {
            // 初始化通用类型映射
            initTypeMapping();

            // 初始化数据库特定前缀映射
            initPrefixMapping();
        }

        private static void initTypeMapping() {
            // 字符串类型
            TYPE_MAPPING.put("VARCHAR", "String");
            TYPE_MAPPING.put("CHAR", "String");
            TYPE_MAPPING.put("TEXT", "String");
            TYPE_MAPPING.put("STRING", "String");
            TYPE_MAPPING.put("CLOB", "String");
            TYPE_MAPPING.put("VARCHAR2", "String");
            TYPE_MAPPING.put("NVARCHAR", "String");
            TYPE_MAPPING.put("NVARCHAR2", "String");
            TYPE_MAPPING.put("LONGVARCHAR", "String");

            // 数值类型
            TYPE_MAPPING.put("INT", "Integer");
            TYPE_MAPPING.put("INTEGER", "Integer");
            TYPE_MAPPING.put("TINYINT", "Integer");
            TYPE_MAPPING.put("SMALLINT", "Integer");
            TYPE_MAPPING.put("MEDIUMINT", "Integer");
            TYPE_MAPPING.put("BIGINT", "Long");
            TYPE_MAPPING.put("NUMBER", "BigDecimal");
            TYPE_MAPPING.put("DECIMAL", "BigDecimal");
            TYPE_MAPPING.put("NUMERIC", "BigDecimal");
            TYPE_MAPPING.put("FLOAT", "Float");
            TYPE_MAPPING.put("DOUBLE", "Double");
            TYPE_MAPPING.put("REAL", "Double");

            // 二进制类型
            TYPE_MAPPING.put("BLOB", "byte[]");
            TYPE_MAPPING.put("BINARY", "byte[]");
            TYPE_MAPPING.put("VARBINARY", "byte[]");
            TYPE_MAPPING.put("LONGVARBINARY", "byte[]");
            TYPE_MAPPING.put("RAW", "byte[]");

            //", "byte[]");

            // 布尔类型
            TYPE_MAPPING.put("BIT", "Boolean");
            TYPE_MAPPING.put("BOOLEAN", "Boolean");

            // 日期时间类型 - 使用Java 8 API
            TYPE_MAPPING.put("DATE", "LocalDate");
            TYPE_MAPPING.put("TIME", "LocalTime");
            TYPE_MAPPING.put("DATETIME", "LocalDateTime");
            TYPE_MAPPING.put("TIMESTAMP", "LocalDateTime");
            TYPE_MAPPING.put("TIMESTAMPTZ", "ZonedDateTime");
            TYPE_MAPPING.put("TIMESTAMPLTZ", "ZonedDateTime");
            TYPE_MAPPING.put("YEAR", "Year");
            TYPE_MAPPING.put("INTERVAL YEAR TO MONTH", "Period");
            TYPE_MAPPING.put("INTERVAL DAY TO SECOND", "Duration");
        }

        private static void initPrefixMapping() {
            // 达梦数据库特定类型前缀
            PREFIX_MAPPING.put("VARCHAR", "String");
            PREFIX_MAPPING.put("NVARCHAR", "String");
            PREFIX_MAPPING.put("NUMBER", "BigDecimal");
            PREFIX_MAPPING.put("TIMESTAMP", "LocalDateTime");

            // Oracle特定类型前缀
            PREFIX_MAPPING.put("VARCHAR2", "String");
            PREFIX_MAPPING.put("NVARCHAR2", "String");
            PREFIX_MAPPING.put("TIMESTAMP WITH TIME ZONE", "ZonedDateTime");
            PREFIX_MAPPING.put("TIMESTAMP WITH LOCAL TIME ZONE", "ZonedDateTime");
        }

        /**
         * 将数据库类型映射为Java类型
         *
         * @param dbType     数据库类型名称
         * @param columnSize 列大小（用于确定TINYINT(1)是否为boolean）
         * @return 对应的Java类型全限定名
         */
        public static String getJavaType(String dbType, Long columnSize) {
            if (dbType == null || dbType.isEmpty()) {
                return "Object";
            }

            // 统一转为大写处理
            String upperDbType = dbType.toUpperCase();

            // 1. 尝试精确匹配
            String javaType = TYPE_MAPPING.get(upperDbType);
            if (javaType != null) {
                return handleSpecialCases(javaType, upperDbType, columnSize);
            }

            // 2. 处理带括号的类型（如VARCHAR(255)）
            int parenIndex = upperDbType.indexOf('(');
            if (parenIndex > 0) {
                String baseType = upperDbType.substring(0, parenIndex).trim();

                // 检查基本类型是否在映射表中
                javaType = TYPE_MAPPING.get(baseType);
                if (javaType != null) {
                    return handleSpecialCases(javaType, baseType, columnSize);
                }

                // 检查前缀映射
                for (Map.Entry<String, String> entry : PREFIX_MAPPING.entrySet()) {
                    if (baseType.startsWith(entry.getKey())) {
                        return entry.getValue();
                    }
                }
            }

            // 3. 检查前缀映射（针对不带括号的类型）
            for (Map.Entry<String, String> entry : PREFIX_MAPPING.entrySet()) {
                if (upperDbType.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }

            // 4. 默认返回Object
            return "Object";
        }

        /**
         * 处理特殊情况的类型映射
         */
        private static String handleSpecialCases(String javaType, String dbType, Long columnSize) {
            // 处理TINYINT(1) -> Boolean
            if ("Integer" .equalsIgnoreCase(javaType) && "TINYINT" .equalsIgnoreCase(dbType) && columnSize == 1) {
                return "Boolean";
            }

            // 处理达梦的BIT(1) -> Boolean
            if ("byte[]" .equalsIgnoreCase(javaType) && "BIT" .equalsIgnoreCase(dbType) && columnSize == 1) {
                return "Boolean";
            }

            // Oracle的DATE类型包含时间部分
            if ("LocalDate" .equalsIgnoreCase(javaType) && "DATE" .equalsIgnoreCase(dbType)) {
                return "LocalDateTime";
            }

            return javaType;
        }

        /**
         * 获取需要导入的包列表
         */
        public static List<String> getRequiredImports(String javaType) {
            List<String> imports = new ArrayList<>();

            switch (javaType) {
                case "LocalDate":
                    imports.add("java.time.LocalDate");
                    break;
                case "LocalTime":
                    imports.add("java.time.LocalTime");
                    break;
                case "LocalDateTime":
                    imports.add("java.time.LocalDateTime");
                    break;
                case "ZonedDateTime":
                    imports.add("java.time.ZonedDateTime");
                    break;
                case "Year":
                    imports.add("java.time.Year");
                    break;
                case "Period":
                    imports.add("java.time.Period");
                    break;
                case "Duration":
                    imports.add("java.time.Duration");
                    break;
                case "BigDecimal":
                    imports.add("java.math.BigDecimal");
                    break;
            }

            return imports;
        }
    }
}
