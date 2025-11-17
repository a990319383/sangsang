package com.sangsang.cache.fieldparse;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sangsang.cache.fielddefault.FieldDefaultInstanceCache;
import com.sangsang.config.other.DefaultBeanPostProcessor;
import com.sangsang.config.properties.FieldProperties;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.annos.encryptor.ShardingTableEncryptor;
import com.sangsang.domain.annos.fielddefault.FieldDefault;
import com.sangsang.domain.annos.isolation.DataIsolation;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.constants.TransformationPatternTypeConstant;
import com.sangsang.domain.dto.TableFieldDto;
import com.sangsang.domain.dto.TableInfoDto;
import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.domain.wrapper.FieldHashSetWrapper;
import com.sangsang.util.*;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 优先加载这个bean，避免有些@PostConstruct 加载数据库东西到redis时，此时还没处理完，导致redis中存储了密文
 * 记录表，字段，字段对应的加解密信息
 * 核心缓存类
 *
 * @author liutangqi
 * @date 2024/2/1 13:27
 */
@Slf4j
public class TableCache extends DefaultBeanPostProcessor {

    private static FieldProperties fieldProperties;

    /**
     * key: 表名  value: (key:字段名  value: 实体类上标注的@FieldEncryptor注解)
     */
    private static final Map<String, Map<String, FieldEncryptor>> TABLE_ENTITY_CACHE = new FieldHashMapWrapper<>();

    /**
     * key: 表名 value: (key:字段名  value: 实体类上标注的@FieldDefault注解)
     */
    private static final Map<String, Map<String, FieldDefault>> TABLE_DEFAULT_CACHE = new FieldHashMapWrapper<>();

    /**
     * 缓存当前表头上的注解
     * key: 表名
     * value: 表头上的注解
     **/
    private static final Map<String, DataIsolation> TABLE_ISOLATION_ANNO_MAP = new FieldHashMapWrapper<>();

    /**
     * 有字段需要加解密的表名的集合
     */
    private static final Set<String> FIELD_ENCRYPT_TABLE = new FieldHashSetWrapper();

    /**
     * 有字段需要进行默认值处理的表名集合
     */
    private static final Set<String> FIELD_DEFAULT_TABLE = new FieldHashSetWrapper();

    /**
     * 存储当前需要进行数据隔离的表名
     *
     * @author liutangqi
     * @date 2025/7/3 10:36
     * @Param
     **/
    private static final Set<String> ISOLATION_TABLE = new FieldHashSetWrapper();

    /**
     * key: 表名
     * value: 该表所有的字段
     */
    private static final Map<String, Set<String>> TABLE_FIELD_MAP = new FieldHashMapWrapper<>();

    /**
     * 初始化当前表结构信息
     *
     * @param dataSources     当前项目的
     * @param fieldProperties
     * @author liutangqi
     * @date 2024/2/1 13:27
     **/
    public static void init(List<DataSource> dataSources, FieldProperties fieldProperties) {
        long startTime = System.currentTimeMillis();
        //1.处理当前项目的数据库标识符的引用符，并缓存当前项目配置
        TableCache.fieldProperties = fillIdentifierQuote(fieldProperties, dataSources);

        //2.扫描配置的路径校验
        if (com.sangsang.util.CollectionUtils.isEmpty(fieldProperties.getScanEntityPackage())) {
            log.warn("【field-encryptor】当前未配置实体类扫描路径，如需使用字段脱敏以外的功能，请完善此配置");
            return;
        }

        //3.开始扫描配置路径的实体类
        List<TableInfoDto> tableInfoDtos = fieldProperties.getScanEntityPackage().stream().map(m -> parseTableInfoByScanEntityPackage(m)).flatMap(Collection::stream).collect(Collectors.toList());
        log.info("【field-encryptor】初始化表结构信息，扫描指定包路径 :{} 合计表数量：{}", fieldProperties.getScanEntityPackage(), tableInfoDtos.size());

        //4.将表结构信息处理，加载到缓存的各个Map中
        fillCacheMap(tableInfoDtos);

        //5.如果开启了表结构填充配置的话，则进行表结构填充
        if (fieldProperties.isAutoFill()) {
            FieldFillUtil.fieldFill(dataSources, fieldProperties);
        }

        //6.去掉不必要表的缓存，避免某些逻辑进行不必要的循环
        simplificationCache(fieldProperties);

        log.info("【field-encryptor】初始化表结构信息，处理完毕 耗时：{}ms", (System.currentTimeMillis() - startTime));
    }

    /**
     * 去掉不必要表的缓存，避免某些逻辑进行不必要的循环
     * 当项目中使用到了语法转换，则不进行精简，因为语法转化需要使用的整个库的全部表结构信息
     * 其它功能目前只需要对应的表的字段是全的即可
     *
     * @author liutangqi
     * @date 2025/11/14 16:11
     * @Param []
     **/
    private static void simplificationCache(FieldProperties fieldProperties) {
        //1.如果开启了语法转换功能，则不进行精简，因为这个功能需要使用到整个库的全部表结构
        if (fieldProperties.getTransformation() != null && StringUtils.isNotBlank(fieldProperties.getTransformation().getPatternType())) {
            return;
        }

        //2.过滤只要我们功能中需要的表
        ((FieldHashMapWrapper) TABLE_FIELD_MAP)
                .filter(f -> TableCache.getCurConfigTable().contains(f.getKey()));
    }

    /**
     * 如果当前项目没有配置identifierQuote，则从第一个dataSource中获取
     *
     * @author liutangqi
     * @date 2025/11/5 17:31
     * @Param [fieldProperties, dataSources]
     **/
    private static FieldProperties fillIdentifierQuote(FieldProperties fieldProperties, List<DataSource> dataSources) {
        //1.如果手动指定了标识符的引用符，或者当前无法获取 DataSource，则返回
        if (StringUtils.isNotBlank(fieldProperties.getIdentifierQuote())) {
            return fieldProperties;
        }

        //2.如果当前开启了语法转换功能的话，默认引用符为mysql的 ` ，从DataSource里面获取的话，肯定是达梦的 " ，老项目里面用的肯定是mysql的 `
        if (fieldProperties.getTransformation() != null && TransformationPatternTypeConstant.MYSQL_2_DM.equals(fieldProperties.getTransformation().getPatternType())) {
            fieldProperties.setIdentifierQuote(SymbolConstant.FLOAT);
            log.info("【field-encryptor】当前启用了mysql自动转换达梦数据库，且未指定identifierQuote数据库标识引用符，默认使用mysql的 `，项目中表名字段名需要标识引用符引起来时请使用 ` !!!");
            return fieldProperties;
        }

        //3.如果当前没有手动指定标识符引用符，且无法获取DataSource对象，则进行日志警告提醒
        if (CollectionUtils.isEmpty(dataSources)) {
            log.warn("【field-encryptor】当前无法自动获取数据源的标识符的引用符，请手动配置 field.identifierQuote  备注：mysql是 ` 达梦，oracle等是\"");
            return fieldProperties;
        }

        //4.从第一个DataSource中获取
        try (Connection conn = dataSources.get(0).getConnection()) {
            String identifierQuoteString = conn.getMetaData().getIdentifierQuoteString();
            fieldProperties.setIdentifierQuote(identifierQuoteString);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return fieldProperties;
    }

    /**
     * 通过指定包路径，扫描路径下所拥有@TableName的全部实体类的字段信息
     *
     * @author liutangqi
     * @date 2024/5/17 13:29
     * @Param [scanEntityPackage]
     **/
    private static List<TableInfoDto> parseTableInfoByScanEntityPackage(String scanEntityPackage) {
        List<TableInfoDto> result = new ArrayList<>();
        //1.扫描指定路径下，所有标注有@TableName注解的类
        Set<Class> entityClasses = ClassScanerUtil.scan(scanEntityPackage, TableName.class);

        for (Class entityClass : entityClasses) {
            //2.获取类的所有字段
            List<Field> allFields = ReflectUtils.getAllFields(entityClass);

            //3.过滤掉不属于实体类的字段，过滤掉static修饰的字段
            allFields = allFields.stream().filter(f -> f.getAnnotation(TableField.class) == null || f.getAnnotation(TableField.class).exist()).filter(f -> !Modifier.isStatic(f.getModifiers())).collect(Collectors.toList());

            //4.解析字段对应数据库字段名和标注的加解密注解
            Set<TableFieldDto> tableFieldDtos = allFields.stream().map(field -> {
                //4.1 解析获取数据库字段名
                String filedName = Optional.ofNullable(field.getAnnotation(TableField.class)).filter(f -> StringUtils.isNotBlank(f.value())).map(m -> m.value()).orElse(StrUtil.toUnderlineCase(field.getName()));
                //4.2获取此字段上拥有的@FieldEncryptor 注解
                FieldEncryptor fieldEncryptor = field.getAnnotation(FieldEncryptor.class);
                //4.3获取此字段上拥有的@FieldDefault 注解
                FieldDefault fieldDefault = field.getAnnotation(FieldDefault.class);
                //4.4构建字段对象
                return TableFieldDto.builder().fieldName(filedName).fieldEncryptor(fieldEncryptor).fieldDefault(fieldDefault).build();
            }).collect(Collectors.toSet());

            //5.获取表名
            TableName tableName = (TableName) entityClass.getAnnotation(TableName.class);

            //6.获取实体类上标注的@DataIsolation
            DataIsolation dataIsolation = (DataIsolation) entityClass.getAnnotation(DataIsolation.class);

            //7.组装结果集
            result.add(TableInfoDto.builder().tableName(tableName.value()).tableFields(tableFieldDtos).dataIsolation(dataIsolation).build());

            //8.如果当前类是分表的类的话(标注了@ShardingTableEncryptor)，将此表的所有分表信息也一起添加
            ShardingTableEncryptor shardingTableEncryptor = (ShardingTableEncryptor) entityClass.getAnnotation(ShardingTableEncryptor.class);
            if (shardingTableEncryptor != null) {
                try {
                    List<String> shardingTableName = shardingTableEncryptor.value().newInstance().getShardingTableName(tableName.value());
                    shardingTableName.stream().forEach(f -> result.add(TableInfoDto.builder().tableName(f).tableFields(tableFieldDtos).dataIsolation(dataIsolation).build()));
                } catch (Exception e) {
                    log.error("【field-encryptor】 @ShardingTableEncryptor 分表表名策略实例化失败，请确保策略有无参构造方法", e);
                }
            }
        }
        return result;

    }


    /**
     * 将表结构信息填充到缓存的Map中
     *
     * @author liutangqi
     * @date 2024/5/17 14:09
     * @Param [tableInfoDtos]
     **/
    private static void fillCacheMap(List<TableInfoDto> tableInfoDtos) {
        //TABLE_ENTITY_CACHE
        tableInfoDtos.stream().forEach(f -> {
            Map<String, FieldEncryptor> fieldMap = new FieldHashMapWrapper<>();
            f.getTableFields().stream().forEach(field -> fieldMap.put(field.getFieldName(), field.getFieldEncryptor()));
            TABLE_ENTITY_CACHE.put(f.getTableName(), fieldMap);
        });

        //TABLE_DEFAULT_CACHE
        tableInfoDtos.stream().forEach(f -> {
            Map<String, FieldDefault> fieldMap = new FieldHashMapWrapper<>();
            f.getTableFields().stream().forEach(field -> fieldMap.put(field.getFieldName(), field.getFieldDefault()));
            TABLE_DEFAULT_CACHE.put(f.getTableName(), fieldMap);
        });

        //TABLE_ISOLATION_ANNO_MAP
        tableInfoDtos.stream().forEach(f -> TABLE_ISOLATION_ANNO_MAP.put(f.getTableName(), f.getDataIsolation()));

        //FIELD_ENCRYPT_TABLE
        tableInfoDtos.stream().filter(f -> f.getTableFields().stream().filter(field -> field.getFieldEncryptor() != null).count() > 0).map(TableInfoDto::getTableName).forEach(f -> FIELD_ENCRYPT_TABLE.add(f));

        //FIELD_DEFAULT_TABLE
        tableInfoDtos.stream().filter(f -> f.getTableFields().stream().filter(field -> field.getFieldDefault() != null).count() > 0).map(TableInfoDto::getTableName).forEach(f -> FIELD_DEFAULT_TABLE.add(f));

        //ISOLATION_TABLE
        tableInfoDtos.stream().filter(f -> f.getDataIsolation() != null).forEach(f -> ISOLATION_TABLE.add(f.getTableName()));

        //TABLE_FIELD_MAP
        tableInfoDtos.stream()
                .forEach(f -> TABLE_FIELD_MAP.put(f.getTableName(), f.getTableFields()
                        .stream()
                        .map(TableFieldDto::getFieldName)
                        .collect(FieldHashSetWrapper::new, Set::add, Set::addAll)
                ));
    }


    //--------------------------------------------下面是对外提供的方法---------------------------------------------------------------

    /**
     * 获取当前项目所有表字段是否加密的信息
     *
     * @author liutangqi
     * @date 2024/2/1 14:07
     * @Param []
     **/
    public static Map<String, Map<String, FieldEncryptor>> getTableFieldEncryptInfo() {
        return TABLE_ENTITY_CACHE;
    }

    /**
     * 获取当前项目所有表字段想要变更时维护的默认值信息
     *
     * @author liutangqi
     * @date 2025/7/17 9:25
     * @Param []
     **/
    public static Map<String, Map<String, FieldDefault>> getTableFieldDefaultInfo() {
        return TABLE_DEFAULT_CACHE;
    }


    /**
     * 获取当前项目的表中标注的@DataIsolation信息
     *
     * @author liutangqi
     * @date 2025/8/25 16:39
     * @Param []
     **/
    public static Map<String, DataIsolation> getTableIsolationInfo() {
        return TABLE_ISOLATION_ANNO_MAP;
    }

    /**
     * 获取当前有字段需要加密的所有表
     *
     * @author liutangqi
     * @date 2024/2/20 10:36
     * @Param []
     **/
    public static Set<String> getFieldEncryptTable() {
        return FIELD_ENCRYPT_TABLE;
    }

    /**
     * 获取当前存在字段需要进行默认值设置的表名
     *
     * @author liutangqi
     * @date 2025/7/17 10:01
     * @Param []
     **/
    public static Set<String> getFieldDefaultTable() {
        return FIELD_DEFAULT_TABLE;
    }

    /**
     * 获取当前项目中有需要进行数据隔离的表名
     *
     * @author liutangqi
     * @date 2025/8/25 16:40
     * @Param []
     **/
    public static Set<String> getIsolationTable() {
        return ISOLATION_TABLE;
    }

    /**
     * 获取当前表拥有的所有字段
     *
     * @author liutangqi
     * @date 2024/2/20 10:48
     * @Param []
     **/
    public static Map<String, Set<String>> getTableFieldMap() {
        return TABLE_FIELD_MAP;
    }


    /**
     * 获取当前项目的配置
     *
     * @author liutangqi
     * @date 2025/8/15 17:52
     * @Param []
     **/
    public static FieldProperties getCurConfig() {
        return fieldProperties;
    }


    /**
     * 刷新当前的表结构缓存
     *
     * @author liutangqi
     * @date 2025/8/25 14:56
     * @Param [tableFieldMap]
     **/
    public static void refreshTableField(Map<String, Set<String>> tableFieldMap) {
        //1.先清空旧的
        TABLE_FIELD_MAP.clear();
        //2.使用新的替换旧的
        TABLE_FIELD_MAP.putAll(tableFieldMap);
    }

    /**
     * 获取到当前项目功能涉及到的表名
     * 加解密+设置默认值+数据隔离
     *
     * @author liutangqi
     * @date 2025/11/14 16:14
     * @Param []
     **/
    public static FieldHashSetWrapper getCurConfigTable() {
        return Stream.of(TableCache.getFieldEncryptTable(),
                        TableCache.getFieldDefaultTable(),
                        TableCache.getIsolationTable())
                .flatMap(Collection::stream)
                .collect(FieldHashSetWrapper::new, Set::add, Set::addAll);
    }

}
