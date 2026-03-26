package com.sangsang.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.db.meta.MetaUtil;
import cn.hutool.db.meta.Table;
import com.sangsang.cache.encryptor.EncryptorInstanceCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.dto.GenerateDto;
import com.sangsang.domain.dto.TableFieldMsgDto;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.schema.Column;

import javax.sql.DataSource;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 生成加解密表备份DDL,DML 的工具类
 *
 * @author liutangqi
 * @date 2024/8/22 13:29
 */
@Slf4j
public class EncryptorBakUtil {

    /**
     * 生成备份表的建表语句
     * 注意：调用时请确保自己数据库密文存储字段标注完成，并且在spring环境启动后调用此方法
     *
     * @param url               连接数据库的地址信息 栗如：jdbc:mysql://127.0.0.1:3306/your_database
     * @param username          连接数据库的用户名
     * @param password          连接数据库的密码
     * @param suffix            备份表的后缀
     * @param expansionMultiple 密文存储字段的扩容倍数，建议值5
     * @param outPath           生成的sql文件路径
     * @author liutangqi
     * @date 2024/8/26 16:12
     **/
    public static void bakSql(String url, String username, String password, String suffix, Integer expansionMultiple, String outPath) {
        //1.获取当前连接的所有表（这里用线程池跑的）
        List<Table> tableList = getTableList(url, username, password);

        //2.格式转换
        List<TableFieldMsgDto> tableColumnList = tableList.stream()
                .map(t -> t.getColumns().stream()
                        .map(c -> TableFieldMsgDto.builder()
                                .tableName(t.getTableName())
                                .columnName(c.getName())
                                .dataType(c.getTypeName())
                                .pk(c.isPk())
                                .fieldLength(c.getSize())
                                .columnComment(c.getComment())
                                .build())
                        .collect(Collectors.toList())
                ).flatMap(Collection::stream)
                .collect(Collectors.toList());


        //3.过滤出需要加解密的表的主键和需要加解密的字段
        List<TableFieldMsgDto> tableFieldMsgList = tableColumnList.stream()
                .filter(f -> TableCache.getFieldEncryptTable().contains(f.getTableName()))
                .filter(f -> f.isPk() //主键
                        ||
                        TableCache.getTableFieldEncryptInfo()
                                .getOrDefault(f.getTableName(), new FieldHashMapWrapper<>())
                                .getOrDefault(f.getColumnName(), null) != null //需要加解密的字段
                ).peek(p -> p.setFieldEncryptor(TableCache.getTableFieldEncryptInfo().get(p.getTableName()).get(p.getColumnName())))
                .collect(Collectors.toList());

        //4.根据表名进行分组
        Map<String, List<TableFieldMsgDto>> tableFieldMsgeMap = tableFieldMsgList.stream().collect(Collectors.groupingBy(TableFieldMsgDto::getTableName));

        //5.处理改有的sql脚本
        StringBuilder bakTabDDL = new StringBuilder();//备份表ddl
        StringBuilder expansionOrigDDL = new StringBuilder();//原表字段长度扩长的ddl
        StringBuilder bakTabInitDML = new StringBuilder();//初始化备份表dml
        StringBuilder updateOrigDML = new StringBuilder();//根据初始化备份表的数据对原表进行加密
        StringBuilder rollBackDML = new StringBuilder();//出问题对原表数据进行回滚

        for (Map.Entry<String, List<TableFieldMsgDto>> tableFieldEntry : tableFieldMsgeMap.entrySet()) {
            //5.1.处理表名
            String bakTableName = tableFieldEntry.getKey() + "_bak_" + suffix;

            //5.2.创建备份表的ddl
            String ddlCreateBakTable = ddlCreateBakTable(tableFieldEntry, bakTableName, expansionMultiple);
            bakTabDDL.append(ddlCreateBakTable).append("\n\r");

            //5.3.创建扩展原表字段长度的ddl
            String ddlExpansionField = ddlExpansionField(tableFieldEntry, expansionMultiple);
            expansionOrigDDL.append(ddlExpansionField).append("\n\r");

            //5.4.创建备份表初始化的DML
            String dmlInitBakTable = dmlInitBakTable(tableFieldEntry, bakTableName);
            bakTabInitDML.append(dmlInitBakTable).append("\n\r");

            //5.5.创建根据备份表清洗原表数据DML
            String dmlUpdateTable = dmlUpdateTable(tableFieldEntry, bakTableName);
            updateOrigDML.append(dmlUpdateTable).append("\n\r");

            //5.6.创建根据备份表回滚数据DML
            String dmlRollBackTable = dmlRollBackTable(tableFieldEntry, bakTableName);
            rollBackDML.append(dmlRollBackTable).append("\n\r");
        }
        //6.将对应的sql脚本写入文件
        FileUtil.writeUtf8String(bakTabDDL.toString(), outPath + File.separator + "bakTab_DDL.sql");
        FileUtil.writeUtf8String(expansionOrigDDL.toString(), outPath + File.separator + "expansionOrig_DDL.sql");
        FileUtil.writeUtf8String(bakTabInitDML.toString(), outPath + File.separator + "bakTabInit_DML.sql");
        FileUtil.writeUtf8String(updateOrigDML.toString(), outPath + File.separator + "updateOrig_DML.sql");
        FileUtil.writeUtf8String(rollBackDML.toString(), outPath + File.separator + "rollBack_DML.sql");
        log.info("------------------脚本生成完毕------------------");
    }


    /**
     * 获取当前项目配置的需要进行密文存储的表结构信息
     *
     * @author liutangqi
     * @date 2025/8/20 14:48
     * @Param [url, username, password]
     **/
    private static List<Table> getTableList(String url, String username, String password) {
        List<Table> res = Collections.synchronizedList(new ArrayList<>());
        try {
            //1.获取数据库资源
            DataSource dataSource = new SimpleDataSource(url, username, password);

            //2.获取所有的表名
            List<String> tableNames = EntityGenerateUtil.getTableNames(dataSource, GenerateDto.builder().build());

            //3.过滤需要加解密的表
            tableNames = tableNames.stream()
                    .filter(t -> TableCache.getFieldEncryptTable().contains(t))
                    .collect(Collectors.toList());

            //4.获取所有的表结构
            List<CompletableFuture> futures = new ArrayList<>();
            for (String tableName : tableNames) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    res.add(MetaUtil.getTableMeta(dataSource, tableName));
                });
                futures.add(future);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return res;
    }

    /**
     * 创建根据备份表回滚数据DML
     *
     * @author liutangqi
     * @date 2024/8/27 15:24
     * @Param [tableFieldEntry, bakTableName]
     **/
    private static String dmlRollBackTable(Map.Entry<String, List<TableFieldMsgDto>> tableFieldEntry, String bakTableName) {
        //获取到主键字段
        String primaryKey = tableFieldEntry.getValue()
                .stream()
                .filter(f -> f.isPk())
                .findAny()
                .get()
                .getColumnName();

        String sql = "update " + tableFieldEntry.getKey() + " t \n\r  join " + bakTableName + " bak \n\r on t." + primaryKey + " = bak." + primaryKey + "\n\r set ";
        //过滤掉主键
        List<TableFieldMsgDto> tableFieldMsgDtos = tableFieldEntry.getValue().stream().filter(f -> !f.isPk()).collect(Collectors.toList());
        for (TableFieldMsgDto tableFieldMsgDto : tableFieldMsgDtos) {
            sql = sql + "t." + tableFieldMsgDto.getColumnName() + " = bak." + tableFieldMsgDto.getColumnName() + ",";
        }

        //去除“,”
        sql = sql.substring(0, sql.lastIndexOf(","));
        return sql + ";";
    }

    /**
     * 创建根据备份表清洗原表数据DML
     *
     * @author liutangqi
     * @date 2024/8/27 15:24
     * @Param [tableFieldEntry, bakTableName]
     **/
    private static String dmlUpdateTable(Map.Entry<String, List<TableFieldMsgDto>> tableFieldEntry, String bakTableName) {
        //获取到主键字段
        String primaryKey = tableFieldEntry.getValue()
                .stream()
                .filter(f -> f.isPk())
                .findAny()
                .get()
                .getColumnName();

        String sql = "update " + tableFieldEntry.getKey() + " t \n\r  join " + bakTableName + " bak \n\r on t." + primaryKey + " = bak." + primaryKey + "\n\r set ";
        //过滤掉主键
        List<TableFieldMsgDto> tableFieldMsgDtos = tableFieldEntry.getValue().stream().filter(f -> !f.isPk()).collect(Collectors.toList());
        for (TableFieldMsgDto tableFieldMsgDto : tableFieldMsgDtos) {
            String encryptionField = EncryptorInstanceCache.getInstance(tableFieldMsgDto.getFieldEncryptor().value()).encryption(new Column("bak." + tableFieldMsgDto.getColumnName())).toString();
            sql = sql + "t." + tableFieldMsgDto.getColumnName() + " = " + encryptionField + ",";
        }

        //去除“,”
        sql = sql.substring(0, sql.lastIndexOf(","));
        return sql + ";";
    }

    /**
     * 生成备份表的初始化dml sql
     *
     * @author liutangqi
     * @date 2024/8/27 14:49
     * @Param [tableFieldEntry, bakTableName]
     **/
    private static String dmlInitBakTable(Map.Entry<String, List<TableFieldMsgDto>> tableFieldEntry, String bakTableName) {
        String fieldList = "";
        for (TableFieldMsgDto tableFieldMsgDto : tableFieldEntry.getValue()) {
            fieldList = fieldList + tableFieldMsgDto.getColumnName() + ",";
        }
        //去除“,”
        fieldList = fieldList.substring(0, fieldList.lastIndexOf(","));

        String sql = "INSERT INTO " + bakTableName + "(" + fieldList + ")\n\r (select " + fieldList + " from " + tableFieldEntry.getKey() + ");";
        return sql;
    }


    /**
     * 生成创建备份表的ddl
     *
     * @author liutangqi
     * @date 2024/8/27 9:36
     * @Param [tableFieldEntry key:表名 value:表字段, bakTableName 备份表名字]
     **/
    private static String ddlCreateBakTable(Map.Entry<String, List<TableFieldMsgDto>> tableFieldEntry, String bakTableName, Integer expansionMultiple) {
        //1.处理主键信息
        TableFieldMsgDto primaryKeyField = tableFieldEntry.getValue()
                .stream()
                .filter(f -> f.isPk())
                .findAny()
                .get();
        //1.1 主键类型
        String primaryKeyType = primaryKeyField.getDataType();
        if (SymbolConstant.VARCHAR.equalsIgnoreCase(primaryKeyType)) {
            primaryKeyType = "varchar(" + primaryKeyField.getFieldLength() + ")";
        }

        //1.2主键字段名
        String primaryKeyFieldName = primaryKeyField.getColumnName();

        //2.处理加密字段
        String encryptorFieldSql = "";
        //过滤主键
        List<TableFieldMsgDto> tableFieldMsgDtos = tableFieldEntry.getValue().stream().filter(f -> !f.isPk()).collect(Collectors.toList());
        for (TableFieldMsgDto tableFieldMsgDto : tableFieldMsgDtos) {
            String dataType = tableFieldMsgDto.getDataType();
            if (tableFieldMsgDto.getFieldLength() != null) {
                dataType = dataType + "(" + tableFieldMsgDto.getFieldLength() * expansionMultiple + ") ";
            }
            encryptorFieldSql = encryptorFieldSql + "`" + tableFieldMsgDto.getColumnName() + "`  " + dataType + " DEFAULT NULL COMMENT " + "'" + tableFieldMsgDto.getColumnComment() + "',\n\r";
        }

        //3.拼接sql
        String ddl = "CREATE TABLE " + "`" + bakTableName + "` (\n\r"
                + "`" + primaryKeyFieldName + "` " + primaryKeyType + " NOT NULL COMMENT '主键',\n\r"
                + encryptorFieldSql
                + "PRIMARY KEY (`" + primaryKeyFieldName + "`)\n\r"
                + ")COMMENT='" + tableFieldEntry.getKey() + "备份表';";
        return ddl;
    }


    /**
     * 将原表字段进行扩大的ddl
     *
     * @author liutangqi
     * @date 2024/8/27 9:41
     * @Param [tableFieldEntry, encryptorField]
     **/
    private static String ddlExpansionField(Map.Entry<String, List<TableFieldMsgDto>> tableFieldEntry, Integer expansionMultiple) {
        String tableName = tableFieldEntry.getKey();
        String sql = "ALTER TABLE " + tableName + " \n\r";

        //过滤主键
        List<TableFieldMsgDto> tableFieldMsgDtos = tableFieldEntry.getValue().stream().filter(f -> !f.isPk()).collect(Collectors.toList());
        for (TableFieldMsgDto tableFieldMsgDto : tableFieldMsgDtos) {
            String dataType = tableFieldMsgDto.getDataType();
            if (tableFieldMsgDto.getFieldLength() != null) {
                dataType = dataType + "(" + tableFieldMsgDto.getFieldLength() * expansionMultiple + ") ";
            }
            sql = sql + " MODIFY COLUMN " + tableFieldMsgDto.getColumnName() + " " + dataType + " NULL COMMENT '" + tableFieldMsgDto.getColumnComment() + "',";
        }
        sql = sql.substring(0, sql.lastIndexOf(","));
        return sql + ";";
    }
}
