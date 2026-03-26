package com.sangsang.util;

import cn.hutool.db.meta.Column;
import cn.hutool.db.meta.MetaUtil;
import cn.hutool.db.meta.Table;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.config.properties.SangSangProperties;
import com.sangsang.domain.dto.GenerateDto;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.domain.wrapper.FieldLinkedListWarpper;
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
     * @Param [dataSources, sangSangProperties]
     **/
    public static void fieldFill(List<DataSource> dataSources, SangSangProperties sangSangProperties) {
        try {
            long t1 = System.currentTimeMillis();
            log.info("【sangsang】开始自动填充数据库表字段");
            //1.当前项目没有可用datasource，不进行自动填充
            if (CollectionUtils.isEmpty(dataSources)) {
                log.warn("【sangsang】自动填充数据库表字段，当前无可用DataSource，不进行自动填充");
                return;
            }

            //2.获取本项目中需要使用到的所有实体类
            Set<String> needTableNames = TableCache.getCurConfigTable();

            //3.解析出所有库我们需要解析的表的字段信息
            Map<String, List<String>> tableFieldMap = new FieldHashMapWrapper();
            for (DataSource dataSource : dataSources) {
                //3.1 获取所有的表名
                List<String> tableNames = EntityGenerateUtil.getTableNames(dataSource, GenerateDto.builder().build());
                //3.2 如果没有开启语法转换功能的话，只处理我们需要的表名。（因为语法转换这个功能需要整个库的所有表结构信息）
                if (sangSangProperties.getTransformation() == null || StringUtils.isBlank(sangSangProperties.getTransformation().getPatternType())) {
                    tableNames = tableNames.stream().filter(f -> needTableNames.contains(f)).collect(Collectors.toList());
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
                //3.4 将这些表的字段信息维护到tableFieldMap中
                datasourceTableMetas.stream()
                        .filter(f -> CollectionUtils.isNotEmpty(f.getColumns()))
                        .forEach(f -> tableFieldMap.put(f.getTableName(), f.getColumns()
                                .stream()
                                .map(Column::getName)
                                .collect(Collectors.toCollection(FieldLinkedListWarpper::new))));
            }

            //4.将本项目核心缓存TableCache中缓存表结构的信息给替换成处理之后的
            TableCache.refreshTableField(tableFieldMap);
            log.info("【sangsang】自动填充数据库表字段结束 合计维护表：{}张 耗时：{}ms", tableFieldMap.size(), (System.currentTimeMillis() - t1));
        } catch (Exception e) {
            log.error("【sangsang】自动填充数据库表字段异常，请检查当前账号是否有权限，无权限请手动将扫描路径下的实体类及其字段补全", e);
        }
    }

}
