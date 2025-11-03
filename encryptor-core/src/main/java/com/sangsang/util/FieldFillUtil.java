package com.sangsang.util;

import cn.hutool.db.meta.Column;
import cn.hutool.db.meta.MetaUtil;
import cn.hutool.db.meta.Table;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.config.properties.FieldProperties;
import com.sangsang.domain.dto.GenerateDto;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 从当前datasource的信息中获取整个数据库的字段信息
 *
 * @author liutangqi
 * @date 2025/8/25 14:07
 */
@Slf4j
public class FieldFillUtil {

    /**
     * 将整个库的字段信息补充到 TableCache中
     *
     * @author liutangqi
     * @date 2025/8/25 14:08
     * @Param [dataSources, fieldProperties]
     **/
    public static void fieldFill(List<DataSource> dataSources, FieldProperties fieldProperties) {
        try {
            long t1 = System.currentTimeMillis();
            log.info("【field-encryptor】开始自动填充数据库表字段");
            //1.当前项目没有可用datasource，不进行自动填充
            if (CollectionUtils.isEmpty(dataSources)) {
                log.warn("【field-encryptor】自动填充数据库表字段，当前无可用DataSource，不进行自动填充");
                return;
            }

            //2.获取本项目中需要使用到的所有实体类（表名小写）
            Set<String> needTableNames = new HashSet<>();
            //如果开启了语法转换的话，需要整个库的所有表的字段信息，如果没有开启语法转换的话，只需要我们需要的功能中涉及到的表结构信息即可
            if (fieldProperties.getTransformation() == null || StringUtils.isBlank(fieldProperties.getTransformation().getPatternType())) {
                //2.1 加解密涉及的表
                Set<String> fieldEncryptTable = TableCache.getFieldEncryptTable();
                //2.2 变更默认值涉及的表
                Set<String> fieldDefaultTable = TableCache.getFieldDefaultTable();
                //2.3 数据隔离涉及的表
                Set<String> isolationTable = TableCache.getIsolationTable();

                needTableNames.addAll(fieldEncryptTable);
                needTableNames.addAll(fieldDefaultTable);
                needTableNames.addAll(isolationTable);
            }


            //3.解析出所有库我们需要解析的表的字段信息
            Map<String, Set<String>> tableFieldMap = new HashMap<>();
            for (DataSource dataSource : dataSources) {
                //3.1 获取所有的表名
                List<String> tableNames = EntityGenerateUtil.getTableNames(dataSource, GenerateDto.builder().build());
                //3.2 只处理我们需要的表名
                if (CollectionUtils.isNotEmpty(needTableNames)) {
                    tableNames = tableNames.stream().filter(f -> CollectionUtils.containsIgnoreFieldSymbol(needTableNames, f)).collect(Collectors.toList());
                }
                //3.3 使用多线程，读取这些表的字段信息
                List<Table> datasourceTableMetas = Collections.synchronizedList(new ArrayList<>());
                List<CompletableFuture> futures = new ArrayList<>();
                for (String tableName : tableNames) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        datasourceTableMetas.add(MetaUtil.getTableMeta(dataSource, tableName));
                    });
                    futures.add(future);
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
                //3.4 将这些表的字段信息维护到tableFieldMap中，其中表名和字段名均转换为小写
                datasourceTableMetas.stream()
                        .filter(f -> CollectionUtils.isNotEmpty(f.getColumns()))
                        .forEach(f -> tableFieldMap.put(f.getTableName().toLowerCase(), f.getColumns().stream().map(Column::getName).map(String::toLowerCase).collect(Collectors.toSet())));
            }

            //4.将本项目核心缓存TableCache中缓存表结构的信息给替换成处理之后的
            TableCache.refreshTableField(tableFieldMap);
            log.info("【field-encryptor】自动填充数据库表字段结束 合计维护表：{}张 耗时：{}ms", tableFieldMap.keySet().size(), (System.currentTimeMillis() - t1));
        } catch (Exception e) {
            log.error("【field-encryptor】自动填充数据库表字段异常，请检查当前账号是否有权限", e);
        }
    }

}
