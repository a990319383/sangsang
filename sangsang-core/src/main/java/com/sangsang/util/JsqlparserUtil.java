package com.sangsang.util;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.ObjectUtil;
import com.sangsang.cache.SqlParseCache;
import com.sangsang.cache.fielddefault.FieldDefaultInstanceCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.cache.isolation.IsolationInstanceCache;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.annos.fielddefault.FieldDefault;
import com.sangsang.domain.annos.isolation.DataIsolation;
import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.context.TfParameterMappingHolder;
import com.sangsang.domain.dto.*;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.domain.enums.IsolationConditionalRelationEnum;
import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.domain.exception.FieldException;
import com.sangsang.domain.strategy.isolation.DataIsolationStrategy;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.domain.wrapper.LayerHashMapWrapper;
import com.sangsang.visitor.dbencrtptor.DBDecryptExpressionVisitor;
import com.sangsang.visitor.isolation.IsolationExpressionVisitor;
import com.sangsang.visitor.pojoencrtptor.PlaceholderExpressionVisitor;
import com.sangsang.visitor.transformation.TransformationExpressionVisitor;
import com.sangsang.visitor.transformation.wrap.ExpressionWrapper;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 解析sql语句相关的工具类
 *
 * @author liutangqi
 * @date 2024/2/1 14:14
 */
public class JsqlparserUtil {


    private static final Logger log = LoggerFactory.getLogger(JsqlparserUtil.class);

    /**
     * Jsqlparser解析的统一入口，不直接使用原生的CCJSqlParserUtil.parse()
     *
     * @author liutangqi
     * @date 2025/6/18 14:19
     * @Param [sql]
     **/
    public static Statement parse(String sql) throws JSQLParserException {
        //1.从缓存中获取解析结果
        Statement statement = SqlParseCache.getSqlParseCache(sql);

        //2.缓存未命中，去除空白行后进行解析（Jsqlparser4.9中sql中有空白行会解析报错）
        if (statement == null) {
            String clearSql = StringUtils.replaceLineBreak(sql);
            statement = CCJSqlParserUtil.parse(clearSql);
            SqlParseCache.setSqlParseCache(sql, statement);
        }

        //3.将statement深克隆一份给到调用方，这里不能返回缓存对象，缓存对象里面会改，相互影响
        //备注：这里深克隆一份的耗时大约是重新解析一遍的十分之一，所以这里是个有效缓存，这个也是个高频操作，整个系统多种功能都强依赖这个
        return ObjectUtil.cloneByStream(statement);
    }

    /**
     * 加解密时，补齐 select * 的所有字段
     * 备注：只补齐有实体类的表，并且这个实体类存在需要加密字段
     *
     * @param selectItem
     * @param layer                             当前sql层数
     * @param sqlLayerFieldTableMap             当前sql每层拥有的表字段信息
     * @param upstreamNeedEncryptFieldEncryptor 当前sql上游需要加密字段的注解
     * @author liutangqi
     * @date 2024/2/19 17:20
     **/
    public static List<SelectItem> perfectAllColumns(SelectItem selectItem, Integer layer, Map<Integer, Map<String, List<FieldInfoDto>>> sqlLayerFieldTableMap, List<FieldEncryptor> upstreamNeedEncryptFieldEncryptor) {
        //-1.获取sql本层的所有的表字段（不包含上游作用域的字段），因为上游作用域的字段一般是不在select * 中的
        Map<String, List<FieldInfoDto>> layerFieldTableMap = sqlLayerFieldTableMap.get(layer);
        if (sqlLayerFieldTableMap instanceof LayerHashMapWrapper) {
            layerFieldTableMap = ((LayerHashMapWrapper) sqlLayerFieldTableMap).getExclusiveUpstreamScope(layer);
        }

        //0.判断上游字段是否需要密文存储，受上游对应字段影响，某些不需要密文存储的字段也需要进行加密处理
        boolean upstreamNeedEncrypt = upstreamNeedEncryptFieldEncryptor != null && upstreamNeedEncryptFieldEncryptor.stream().filter(f -> f != null).collect(Collectors.toList()).size() > 0;

        //1. select 别名.*  （注意：AllColumns AllTableColumns 有继承关系，这里判断顺序不能改）
        Expression expression = selectItem.getExpression();
        if (expression instanceof AllTableColumns) {
            String tableName = ((AllTableColumns) expression).getTable().getName();
            List<FieldInfoDto> fieldInfoSet = layerFieldTableMap.get(tableName);
            //1.1没有配置此表的字段信息，返回原表达式
            if (CollectionUtils.isEmpty(fieldInfoSet)) {
                return Arrays.asList(selectItem);
            }
            //1.2 缓存中有此表的字段信息，此表是一个虚拟表 或 （上游不需要密文存储并且此表也不需要密文存储） 则保持原样
            FieldInfoDto fieldInfoDto = new ArrayList<FieldInfoDto>(fieldInfoSet).get(0);
            if (!fieldInfoDto.isFromSourceTable() || (!upstreamNeedEncrypt && !TableCache.getFieldEncryptTable().contains(fieldInfoDto.getSourceTableName()))) {
                return Arrays.asList(selectItem);
            }
            //1.3配置了此表的字段信息，将配置的所有字段去替换*
            return fieldInfoSet.stream().map(m -> {
                Column column = new Column(m.getColumnName());
                column.setTable(new Table(tableName));
                return SelectItem.from(column);
            }).collect(Collectors.toList());
        }

        //2. select *  未指定别名，将当前层的全部字段作为结果集返回 (如果当前层的表需要密文存储，则使用全部字段替换，否则还是用原来的*，不过这里修改为别名.*)
        if (expression instanceof AllColumns) {
            List<SelectItem> selectItems = new ArrayList<>();
            for (Map.Entry<String, List<FieldInfoDto>> fieldInfoEntry : layerFieldTableMap.entrySet()) {
                //2.1 此表字段没有配置拥有哪些字段，则使用 别名.*
                if (CollectionUtils.isEmpty(fieldInfoEntry.getValue())) {
                    SelectItem<?> item = SelectItem.from(new AllTableColumns(new Table(fieldInfoEntry.getKey())));
                    selectItems.add(item);
                }
                //1.2 缓存中有此表的字段信息，此表是一个虚拟表 或 （上游不需要密文存储并且此表也不需要密文存储） 则保持原样
                else if (!new ArrayList<FieldInfoDto>(fieldInfoEntry.getValue()).get(0).isFromSourceTable() || (!upstreamNeedEncrypt && !TableCache.getFieldEncryptTable().contains(new ArrayList<FieldInfoDto>(fieldInfoEntry.getValue()).get(0).getSourceTableName()))) {
                    SelectItem<?> item = SelectItem.from(new AllTableColumns(new Table(fieldInfoEntry.getKey())));
                    selectItems.add(item);
                }
                //2.3 此表配置了表字段信息，并且需要密文存储，使用配置的字段替换*
                else {
                    for (FieldInfoDto fieldInfoDto : fieldInfoEntry.getValue()) {
                        Column column = new Column(fieldInfoDto.getColumnName());
                        column.setTable(new Table(fieldInfoEntry.getKey()));
                        selectItems.add(SelectItem.from(column));
                    }
                }
            }
            return selectItems;
        }

        //3. 不包含*
        return Arrays.asList(selectItem);
    }

    /**
     * 从layerFieldTableMap中解析当前字段所属表的信息
     * 注意：layerFieldTableMap结果集中存储的columName是源字段名，和字段别名无关，所以这里解析的时候直接用Column的名字去匹配
     *
     * @author liutangqi
     * @date 2024/3/6 14:52
     * @Param [column, layer, layerFieldTableMap 每一层的表拥有的全部字段的map]
     **/
    public static ColumnTableDto parseColumn(Column column, BaseFieldParseTable baseFieldParseTable) {
        //字段名
        String columName = column.getColumnName();
        //字段所属表 （只有select 别名.字段名 时这个才有值，其它的为null）
        String tableName = Optional.ofNullable(column.getTable()).map(Table::getName).orElse(null);
        //走匹配的逻辑
        return parseColumn(columName, tableName, baseFieldParseTable.getLayer(), baseFieldParseTable.getLayerFieldTableMap());
    }

    /**
     * 从layerSelectTableFieldMap 中解析当前字段所属表的信息
     * 注意：layerSelectTableFieldMap结果集中存储的columName是有别名优先取别名，没别名取源字段名，要区别开layerFieldTableMap
     * 注意：请区别于方法com.sangsang.util.JsqlparserUtil#parseColumn(net.sf.jsqlparser.schema.Column, int, java.util.Map)
     *
     * @author liutangqi
     * @date 2026/1/8 15:12
     * @Param [column, alias：列的别名, baseFieldParseTable:解析后的结果集]
     **/
    public static ColumnTableDto parseColumn(Column column,
                                             Alias alias,
                                             BaseFieldParseTable baseFieldParseTable) {
        //字段名，有别名取别名，没别名取源字段名
        String columName = Optional.ofNullable(alias).map(Alias::getName).orElse(column.getColumnName());
        //字段所属表 （只有select 别名.字段名 时这个才有值，其它的为null）
        String tableName = Optional.ofNullable(column.getTable()).map(Table::getName).orElse(null);
        //走匹配的逻辑
        return parseColumn(columName, tableName, baseFieldParseTable.getLayer(), baseFieldParseTable.getLayerSelectTableFieldMap());
    }


    /**
     * 从fieldTableMap中找出 字段columnName，所属表为tableName的对应解析字段信息
     * 注意：这里修饰符必须是private，其它地方想要调用只能走上面的重载的方法，不然会导致layerSelectTableFieldMap和layerFieldTableMap混乱使用
     *
     * @param columnName    字段名，注意：两个核心的Map的columnName取值逻辑不同 layerSelectTableFieldMap：有别名取别名，没别名用的原始字段名; layerFieldTableMap：原始字段名
     * @param tableName     字段所属表名
     * @param layer         层级
     * @param fieldTableMap 解析的结果集，layerSelectTableFieldMap 或者是 layerFieldTableMap
     * @author liutangqi
     * @date 2026/1/8 14:15
     **/
    private static ColumnTableDto parseColumn(String columnName,
                                              String tableName,
                                              int layer,
                                              Map<Integer, Map<String, List<FieldInfoDto>>> fieldTableMap) {
        //key: 字段所属的表的别名(from 后面接的表的别名)  value: 匹配成功的字段
        Pair<String, FieldInfoDto> pair = null;
        //1.字段没有所属的表名，则从解析结果集中找命名一样的字段，如果字段没有别名，不从某些数据库的默认归属表里面找（注意：这里前提是没有别名，说明这些伪表别名就只能是数据库自己的，不是用户的sql取的别名）
        if (StringUtils.isBlank(tableName)) {
            pair = fieldTableMap.getOrDefault(layer, new FieldHashMapWrapper<>())
                    .entrySet().stream()
                    .flatMap(entry -> entry.getValue()
                            .stream()
                            .filter(f -> StringUtils.fieldEquals(f.getColumnName(), columnName))
                            .map(m -> Pair.of(entry.getKey(), m)))
                    .findFirst()
                    .orElse(null);
        }

        //2.字段有所属的表名，则从解析结果集中的该表找命名一样的字段
        if (StringUtils.isNotBlank(tableName)) {
            FieldInfoDto fieldInfoDto = fieldTableMap.getOrDefault(layer, new FieldHashMapWrapper<>())
                    .getOrDefault(tableName, Collections.emptyList())
                    .stream()
                    .filter(f -> StringUtils.fieldEquals(f.getColumnName(), columnName))
                    .findFirst()
                    .orElse(null);
            pair = Optional.ofNullable(fieldInfoDto).map(m -> Pair.of(tableName, fieldInfoDto)).orElse(null);
        }

        //3.将匹配到的字段转换为我们想要的结果
        return Optional.ofNullable(pair)
                .map(m -> ColumnTableDto.builder()
                        .tableAliasName(m.getKey())
                        .sourceTableName(m.getValue().getSourceTableName())
                        .sourceColumn(m.getValue().getSourceColumn())
                        .fromSourceTable(m.getValue().isFromSourceTable())
                        .rowNumber(m.getValue().isRowNumber())
                        .build())
                .orElse(ColumnTableDto.DEAFAULT);
    }

    /**
     * 判断这个字段是否需要密文存储，需要的话，返回字段标注的@FieldEncryptor
     * 参考上面这个方法 com.sangsang.util.JsqlparserUtil#needEncrypt(net.sf.jsqlparser.schema.Column, int, java.util.Map)
     *
     * @author liutangqi
     * @date 2025/6/25 16:30
     * @Param [column, layer, layerFieldTableMap]
     **/
    public static FieldEncryptor needEncryptFieldEncryptor(Expression expression, BaseFieldParseTable baseFieldParseTable) {
        //0.不是Column直接返回，其它表达式的话上面是不可能标识得有注解的
        if (!(expression instanceof Column)) {
            return null;
        }

        //1.匹配所属表信息
        Column column = (Column) expression;
        ColumnTableDto columnTableDto = parseColumn(column, baseFieldParseTable);

        //2.当前字段不是直接来源自真实表，而是包了一层的子查询的字段，直接返回，不做处理
        if (!columnTableDto.isFromSourceTable()) {
            return null;
        }

        //3.从当前表字段中找符合条件的字段上面的注解
        return Optional.ofNullable(TableCache.getTableFieldEncryptInfo()).map(m -> m.get(columnTableDto.getSourceTableName())).map(m -> m.get(columnTableDto.getSourceColumn())).orElse(null);
    }

    /**
     * 将 dto 存放到对应的layerTableMap 中
     *
     * @param layer
     * @param tableName
     * @param dto
     * @param layerTableMap key: layer  value( key: tableName value: dto)
     * @author liutangqi
     * @date 2024/3/18 14:17
     **/
    public static void putFieldInfo(Map<Integer, Map<String, List<FieldInfoDto>>> layerTableMap, int layer, String tableName, FieldInfoDto dto) {
        Map<String, List<FieldInfoDto>> layerFieldMap = Optional.ofNullable(layerTableMap.get(layer)).orElse(new FieldHashMapWrapper<>());
        List<FieldInfoDto> fieldInfoDtos = Optional.ofNullable(layerFieldMap.get(tableName)).orElse(new ArrayList<>());

        fieldInfoDtos.add(dto);
        layerFieldMap.put(tableName, fieldInfoDtos);
        layerTableMap.put(layer, layerFieldMap);
    }

    /**
     * 将 dto 存放到对应的layerTableMap 中
     *
     * @param layer
     * @param tableName
     * @param dtos
     * @param layerTableMap key: layer  value( key: tableName value: dtos)
     * @author liutangqi
     * @date 2024/3/18 14:17
     **/
    public static void putFieldInfo(Map<Integer, Map<String, List<FieldInfoDto>>> layerTableMap, int layer, String tableName, List<FieldInfoDto> dtos) {
        Map<String, List<FieldInfoDto>> layerFieldMap = Optional.ofNullable(layerTableMap.get(layer)).orElse(new FieldHashMapWrapper<>());
        List<FieldInfoDto> fieldInfoDtos = Optional.ofNullable(layerFieldMap.get(tableName)).orElse(new ArrayList<>());

        fieldInfoDtos.addAll(dtos);
        layerFieldMap.put(tableName, fieldInfoDtos);
        layerTableMap.put(layer, layerFieldMap);
    }


    /**
     * 解析出newMap 比 oldMap 多的元素
     * 注意：这里的key的判断还是遵循 当前的大小写敏感和当前的关键字符号的配置
     *
     * @author liutangqi
     * @date 2024/3/20 13:54
     * @Param [oldMap, newMap]
     **/
    public static Map<String, List<FieldInfoDto>> parseNewlyIncreased(Map<String, List<FieldInfoDto>> oldMap, Map<String, List<FieldInfoDto>> newMap) {
        Map<String, List<FieldInfoDto>> result = new FieldHashMapWrapper<>();
        for (Map.Entry<String, List<FieldInfoDto>> newMapEntry : newMap.entrySet()) {
            //key 旧的没有，直接整个都是新增的
            if (!oldMap.containsKey(newMapEntry.getKey())) {
                result.put(newMapEntry.getKey(), newMapEntry.getValue());
                //key有，筛选出Set中新增的
            } else {
                List<FieldInfoDto> newlyIncreasedSet = newMapEntry.getValue().stream().filter(f -> !oldMap.get(newMapEntry.getKey()).contains(f)).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(newlyIncreasedSet)) {
                    result.put(newMapEntry.getKey(), newlyIncreasedSet);
                }
            }
        }
        return result;
    }


    /**
     * 根据表和字段信息，从缓存中找到对应字段上面标注的注解
     *
     * @author liutangqi
     * @date 2024/7/24 15:24
     * @Param [dto]
     **/
    public static FieldEncryptor parseFieldEncryptor(ColumnTableDto dto) {
        Map<String, FieldEncryptor> stringFieldEncryptorMap = Optional.ofNullable(TableCache.getTableFieldEncryptInfo().get(dto.getSourceTableName())).orElse(CollectionUtils.EMPTY_FIELD_HASH_MAP);
        return stringFieldEncryptorMap.get(dto.getSourceColumn());
    }


    /**
     * 构建出pojo模式?占位符
     * 备注：
     * --- JdbcParameter 的index是从1开始的
     * ---占位符FieldConstant.PLACEHOLDER后面的序号是从0开始的
     * ---boundSql.getParameterMappings()下标也是从0开始的
     *
     * @author liutangqi
     * @date 2026/10/23 17:12
     * @Param [index]
     **/
    public static String buildPlaceholder(JdbcParameter jdbcParameter) {
        return FieldConstant.PLACEHOLDER + (jdbcParameter.getIndex() - 1);
    }

    /**
     * 根据sql当前层的字段，过滤出其中需要设置变动默认值的字段信息
     *
     * @author liutangqi
     * @date 2025/7/17 11:26
     * @Param [someLayerFieldTableMap: 某一层的字段信息]
     **/
    public static Map<ColumnUniqueDto, FieldDefault> filterFieldDefault(Map<String, List<FieldInfoDto>> someLayerFieldTableMap, SqlCommandEnum sqlCommandEnum) {
        //1.创建处理结果的容器
        Map<ColumnUniqueDto, FieldDefault> res = new HashMap<>();

        //2.当前项目需要设置默认值的表名
        Map<String, Map<String, FieldDefault>> tableFieldDefaultInfo = TableCache.getTableFieldDefaultInfo();

        //3.依次处理每张表信息
        for (Map.Entry<String, List<FieldInfoDto>> fieldMapEntry : someLayerFieldTableMap.entrySet()) {
            String tableAliasName = fieldMapEntry.getKey();
            for (FieldInfoDto fieldInfoDto : fieldMapEntry.getValue()) {
                //获取此字段上面标注的@FieldDefault，存在注解并且当前需要处理 就放结果集里面
                Optional.ofNullable(tableFieldDefaultInfo.get(fieldInfoDto.getSourceTableName()))
                        .map(m -> m.get(fieldInfoDto.getSourceColumn()))
                        .filter(f -> FieldDefaultInstanceCache.getInstance(f.value()).whetherToHandle(sqlCommandEnum))
                        .ifPresent(p -> res.put(ColumnUniqueDto.builder()
                                .tableAliasName(tableAliasName)
                                .sourceTableName(fieldInfoDto.getSourceTableName())
                                .sourceColumn(fieldInfoDto.getSourceColumn())
                                .build(), p));
            }
        }

        return res;
    }


    /**
     * 针对BinaryExpression进行语法解析
     * 备注：只有InExpression 同时拥有左表达式和右表达式，但是不属于BinaryExpression 没有走这个解析
     *
     * @author liutangqi
     * @date 2025/6/6 16:01
     * @Param [tfExpressionVisitor, expression]
     **/
    public static void visitTfBinaryExpression(TransformationExpressionVisitor tfExpressionVisitor, BinaryExpression expression) {
        //解析左表达式
        Expression leftExpression = expression.getLeftExpression();
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExpL = ExpressionWrapper.wrap(leftExpression).accept(tfExpressionVisitor);
        Optional.ofNullable(tfExpL).ifPresent(p -> expression.setLeftExpression(p));

        //解析右表达式
        Expression rightExpression = expression.getRightExpression();
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExpR = ExpressionWrapper.wrap(rightExpression).accept(tfExpressionVisitor);
        Optional.ofNullable(tfExpR).ifPresent(p -> expression.setRightExpression(p));
    }


    /**
     * 针对BinaryExpression进行db模式的解密
     *
     * @author liutangqi
     * @date 2025/6/6 16:20
     * @Param [dbExpressionVisitor, expression]
     **/
    public static void visitDbBinaryExpression(DBDecryptExpressionVisitor dbExpressionVisitor, BinaryExpression expression) {
        //解析左表达式
        Expression leftExpression = expression.getLeftExpression();
        DBDecryptExpressionVisitor leftExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(dbExpressionVisitor);
        leftExpression.accept(leftExpressionVisitor);
        expression.setLeftExpression(Optional.ofNullable(leftExpressionVisitor.getProcessedExpression()).orElse(leftExpression));

        //解析右表达式
        Expression rightExpression = expression.getRightExpression();
        DBDecryptExpressionVisitor rightExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(dbExpressionVisitor);
        rightExpression.accept(rightExpressionVisitor);
        expression.setRightExpression(Optional.ofNullable(rightExpressionVisitor.getProcessedExpression()).orElse(rightExpression));
    }

    /**
     * 针对ComparisonOperator进行db模式的加解密
     *
     * @author liutangqi
     * @date 2025/7/2 17:55
     * @Param [dbExpressionVisitor, expression]
     **/
    public static void visitComparisonOperator(DBDecryptExpressionVisitor dbExpressionVisitor, ComparisonOperator expression) {
        //1.如果左右侧都是 Column 类型的话
        if ((expression.getLeftExpression() instanceof Column) && expression.getRightExpression() instanceof Column) {
            //1.1 两边都需要加密且算法一致时或者两边都不需要加密时，不需要处理
            FieldEncryptor leftFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(expression.getLeftExpression(), dbExpressionVisitor);
            FieldEncryptor rightFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(expression.getRightExpression(), dbExpressionVisitor);
            if (ClassCacheKey.classEquals(Optional.ofNullable(leftFieldEncryptor).map(FieldEncryptor::value).orElse(null), Optional.ofNullable(rightFieldEncryptor).map(FieldEncryptor::value).orElse(null))) {
                return;
            }
            //1.2 两边算法不一致时，处理右边表达式
            DBDecryptExpressionVisitor dbExpVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(dbExpressionVisitor, EncryptorFunctionEnum.UPSTREAM_COLUMN, leftFieldEncryptor);
            expression.getRightExpression().accept(dbExpVisitor);
            //1.3处理结果赋值
            Optional.ofNullable(dbExpVisitor.getProcessedExpression()).ifPresent(p -> expression.setRightExpression(p));
            return;
        }

        //2.左边是 Column 右边不是 Column ，避免索引失效，将非Column进行加密处理即可
        if ((expression.getLeftExpression() instanceof Column) && !(expression.getRightExpression() instanceof Column)) {
            //Column 是需要加密的字段则将非Column进行加密
            FieldEncryptor leftColumnFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(expression.getLeftExpression(), dbExpressionVisitor);
            if (leftColumnFieldEncryptor != null) {
                //将左侧的表达式作为上游对应字段传递给下游的visitor
                DBDecryptExpressionVisitor upstreamDbExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(dbExpressionVisitor, EncryptorFunctionEnum.UPSTREAM_COLUMN, leftColumnFieldEncryptor);
                expression.getRightExpression().accept(upstreamDbExpressionVisitor);
                expression.setRightExpression(Optional.ofNullable(upstreamDbExpressionVisitor.getProcessedExpression()).orElse(expression.getRightExpression()));
            }
            return;
        }

        //3. 左边不是Column 右边是 Column  ，避免索引失效，将非Column进行加密处理即可
        if ((expression.getRightExpression() instanceof Column) && !(expression.getLeftExpression() instanceof Column)) {
            //Column 是需要加密的字段则将非Column进行加密
            FieldEncryptor rightColumnFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(expression.getRightExpression(), dbExpressionVisitor);
            if (rightColumnFieldEncryptor != null) {
                //将右侧的表达式作为上游对应字段传递给下游的visitor
                DBDecryptExpressionVisitor upstreamDbExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(dbExpressionVisitor, EncryptorFunctionEnum.UPSTREAM_COLUMN, rightColumnFieldEncryptor);
                expression.getLeftExpression().accept(upstreamDbExpressionVisitor);
                expression.setLeftExpression(Optional.ofNullable(upstreamDbExpressionVisitor.getProcessedExpression()).orElse(expression.getRightExpression()));
            }
            return;
        }

        //4.其它情况（两边都不是Column） 解析左右两边的表达式
        //db模式处理左右表达式
        JsqlparserUtil.visitDbBinaryExpression(dbExpressionVisitor, expression);
    }

    /**
     * 针对BinaryExpression进行pojo模式的占位符的解析
     *
     * @author liutangqi
     * @date 2025/6/6 16:58
     * @Param [phExpressionVisitor, expression]
     **/
    public static void visitPojoBinaryExpression(PlaceholderExpressionVisitor phExpressionVisitor, BinaryExpression expression) {
        //visitor中上游的表达式不为空的话，则这个visitor不能复用
        PlaceholderExpressionVisitor phVisitor = phExpressionVisitor;
        if (phExpressionVisitor.getUpstreamExpression() != null) {
            phVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(phExpressionVisitor);
        }

        //开始解析左右表达式
        Expression leftExpression = expression.getLeftExpression();
        leftExpression.accept(phVisitor);

        Expression rightExpression = expression.getRightExpression();
        rightExpression.accept(phVisitor);
    }


    /**
     * 补齐设置的字段的默认值
     *
     * @author liutangqi
     * @date 2025/7/22 17:50
     * @Param [upstreamFieldDefaultColumns, expressions]
     **/
    public static ExpressionList completeFiledDefaultValues(List<FieldDefault> upstreamFieldDefaultColumns, ExpressionList<Expression> expressions) {
        for (int i = 0; i < upstreamFieldDefaultColumns.size(); i++) {
            //1.需要设置默认值的字段原sql就存在 && 标注了@FieldDefault && (这个值为null || 开启了强制覆盖) 则将默认值给原sql用case when的方式给替换了（标准sql语法，兼容mysql、oracle、postgresql、达梦等数据库）
            if (i < expressions.size() && upstreamFieldDefaultColumns.get(i) != null && ((expressions.get(i) instanceof NullValue) || upstreamFieldDefaultColumns.get(i).mandatoryOverride())) {
                Expression fieldDefaultExp = ExpressionsUtil.buildFieldDefaultExp(upstreamFieldDefaultColumns.get(i).value());
                Expression caseWhenExp = ExpressionsUtil.buildAffirmative(expressions.get(i), fieldDefaultExp);
                expressions.set(i, caseWhenExp);
            }

            //2.需要设置默认值的字段原sql不存在，则新增
            if (i >= expressions.size() && upstreamFieldDefaultColumns.get(i) != null) {
                Expression fieldDefaultExp = ExpressionsUtil.buildFieldDefaultExp(upstreamFieldDefaultColumns.get(i).value());
                expressions.add(i, fieldDefaultExp);
            }
        }
        return expressions;
    }


    /**
     * 处理数据隔离中的where条件
     *
     * @param curWhere        当前的where条件
     * @param fieldParseTable 当前sql语句解析的结果
     * @author liutangqi
     * @date 2025/12/27 14:22
     **/
    public static Expression isolationWhere(Expression curWhere, BaseFieldParseTable fieldParseTable) {
        //1.处理where条件（主要针对in 子查询和exist）
        if (curWhere != null) {
            curWhere.accept(IsolationExpressionVisitor.newInstanceCurLayer(fieldParseTable));
        }

        //2.处理当前层的数据隔离
        //2.1.存储当前拼接的权限过滤条件(这里list存储的是不同的表的隔离字段)
        List<Expression> isolationExpressions = new ArrayList<>();
        //2.2.获取当前层字段信息
        Map<String, List<FieldInfoDto>> fieldTableMap = fieldParseTable.getLayerFieldTableMap().getOrDefault(fieldParseTable.getLayer(), CollectionUtils.EMPTY_MAP);

        //2.3.判断其中是否存在数据隔离的表
        for (Map.Entry<String, List<FieldInfoDto>> fieldTableEntry : fieldTableMap.entrySet()) {
            //2.3.1 随便获取一个字段，得到这个字段所属的真实表名（因为这些字段都是属于同一张真实表，所以随便获取一个即可），如果这个表所属的表不是来源真实表则直接跳过
            FieldInfoDto anyFieldInfo = fieldTableEntry.getValue().stream().findAny().orElse(null);
            if (anyFieldInfo == null || !anyFieldInfo.isFromSourceTable() || StringUtils.isBlank(anyFieldInfo.getSourceTableName())) {
                continue;
            }
            //2.3.2 通过表名获取到当前的表隔离的相关信息（外层获取，避免方法重复调用），不需要隔离则跳过这个表
            List<DataIsolationStrategy> dataIsolationStrategies = IsolationInstanceCache.getInstance(anyFieldInfo.getSourceTableName());
            DataIsolation dataIsolation = IsolationInstanceCache.getDataIsolationByTableName(anyFieldInfo.getSourceTableName());
            if (CollectionUtils.isEmpty(dataIsolationStrategies) || dataIsolation == null) {
                continue;
            }

            //2.3.3 当前表可能存在多个隔离策略，将其格式转换为key是表字段，value是隔离字段
            Map<String, List<DataIsolationStrategy>> isolationFieldMap = new FieldHashMapWrapper<>();
            dataIsolationStrategies.stream()
                    .forEach(f -> {
                        String isolationField = f.getIsolationField(anyFieldInfo.getSourceTableName());
                        if (StringUtils.isNotBlank(isolationField)) {
                            List<DataIsolationStrategy> strategies = isolationFieldMap.getOrDefault(isolationField, new ArrayList<>());
                            strategies.add(f);
                            isolationFieldMap.put(isolationField, strategies);
                        }
                    });
            if (CollectionUtils.isEmpty(isolationFieldMap)) {
                continue;
            }

            //2.3.4 存储当前表字段的隔离条件拼凑的隔离条件
            List<Expression> tableIsolationExpressions = new ArrayList<>();

            //2.3.5 依次处理每个字段，判断这些字段是否需要数据隔离
            for (FieldInfoDto fieldInfo : fieldTableEntry.getValue()) {
                //2.3.5.1当前字段不是直接来自真实表的，跳过
                if (!fieldInfo.isFromSourceTable()) {
                    continue;
                }
                //2.3.5.2查看当前字段是否需要参与数据隔离
                List<DataIsolationStrategy> dataIsolationStrategy = isolationFieldMap.get(fieldInfo.getSourceColumn());
                if (CollectionUtils.isEmpty(dataIsolationStrategy)) {
                    continue;
                }
                //2.3.5.3开拼
                for (DataIsolationStrategy isolationStrategy : dataIsolationStrategy) {
                    Expression isolationExpression = IsolationInstanceCache.buildIsolationExpression(isolationStrategy.getIsolationField(anyFieldInfo.getSourceTableName())
                            , fieldTableEntry.getKey()
                            , isolationStrategy.getIsolationRelation(anyFieldInfo.getSourceTableName())
                            , isolationStrategy.getIsolationData(anyFieldInfo.getSourceTableName()));
                    //2.3.5.4 将拼接好的条件维护到这个表的隔离条件集合中
                    tableIsolationExpressions.add(isolationExpression);
                }
            }

            //2.3.6 处理这一张表不同的条件
            //2.3.6.1 这张表没有额外加隔离条件，或者只加了一个隔离条件，则不处理，只需要将这个隔离条件加到表级别的list中即可
            if (tableIsolationExpressions.size() <= 1) {
                isolationExpressions.addAll(tableIsolationExpressions);
                continue;
            }
            //2.3.6.1 单表不同字段之间是and
            if (IsolationConditionalRelationEnum.AND.equals(dataIsolation.conditionalRelation())) {
                isolationExpressions.add(ExpressionsUtil.buildAndExpression(tableIsolationExpressions));
            }
            //2.3.6.2 单表不同字段之间是or ，使用or拼接完成后，再将整个表达式使用括号包裹起来
            if (IsolationConditionalRelationEnum.OR.equals(dataIsolation.conditionalRelation())) {
                Expression tableIsoExp = ExpressionsUtil.buildOrExpression(tableIsolationExpressions);
                isolationExpressions.add(ExpressionsUtil.buildParenthesis(tableIsoExp));
            }
        }

        //3.没有需要额外新增的隔离字段吗，则不处理
        if (CollectionUtils.isEmpty(isolationExpressions)) {
            return curWhere;
        }

        //4.处理不同表之间的条件关联关系
        Expression whereIsolation = null;
        if (IsolationConditionalRelationEnum.AND.equals(TableCache.getCurConfig().getIsolation().getConditionalRelation())) {
            whereIsolation = ExpressionsUtil.buildAndExpression(isolationExpressions);
        }
        if (IsolationConditionalRelationEnum.OR.equals(TableCache.getCurConfig().getIsolation().getConditionalRelation())) {
            whereIsolation = ExpressionsUtil.buildOrExpression(isolationExpressions);
        }

        //5.处理where条件
        //5.1 旧sql不存在where
        if (curWhere == null) {
            return whereIsolation;
        }
        //5.2 旧sql存在where， 旧的表达式的关系和现在肯定是and的关系，将旧表达用括号包起来，否则里面存在or的话会有语义错误
        {
            //5.2.1 旧的where 表达式中可能存在or的话，则使用括号包裹起来
            if (!(curWhere instanceof Parenthesis) && StringUtils.containsWholeWordIgnoreCase(curWhere.toString(), "or")) {
                curWhere = ExpressionsUtil.buildParenthesis(curWhere);
            }
            //5.2.2 额外新增的隔离条件中可能存在or的话，使用括号包裹起来
            if (!(whereIsolation instanceof Parenthesis) && StringUtils.containsWholeWordIgnoreCase(whereIsolation.toString(), "or")) {
                whereIsolation = ExpressionsUtil.buildParenthesis(whereIsolation);
            }
            //5.2.3 将旧的where表达式和额外增加的隔离表达式使用and拼接
            AndExpression whereExpression = ExpressionsUtil.buildAndExpression(curWhere, whereIsolation);
            return whereExpression;
        }
    }

    /**
     * 判断当前列是否是行号的关键字字段，一些数据库会用这个进行分页
     *
     * @author liutangqi
     * @date 2026/1/5 11:26
     * @Param [column]
     **/
    public static boolean rowNumber(Column column) {
        return column.getColumnName().equalsIgnoreCase("ROWNUM");
    }


    /**
     * 在语法转换时，尝试将表达式解析成行号的比较值
     * 1.如果当前是数字类型就直接解析返回
     * 2.如果不是数字类型，是占位符开头的，则尝试从当前存储sql入参的ThreadLocal中获取
     * 3.如果是从当前的ThreadLocal中获取的话，表示这个占位符我们不需要了，需要进行写死的替换，则将这个key进行记录
     *
     * @author liutangqi
     * @date 2026/1/9 14:49
     * @Param [exp]
     **/
    public static Long tfParseRowNumber(Expression exp) {
        //1.当前表达式就是LongValue类型的，则直接返回具体的行号比较值
        if (exp instanceof LongValue) {
            return ((LongValue) exp).getValue();
        }

        //2.当前表达式是 JdbcParameter，则则尝试从当前存储sql入参的ThreadLocal中获取
        if (exp instanceof JdbcParameter) {
            JdbcParameter jdbcParameter = (JdbcParameter) exp;
            //2.1 获取当前占位符对应threadLocal中缓存值的key
            String placeholderKey = JsqlparserUtil.buildPlaceholder(jdbcParameter);
            //2.2 从当前记录mapper入参的threadlocal中获取入参值
            Object paramObject = TfParameterMappingHolder.getParameterMapping(placeholderKey);
            if ((paramObject instanceof Long) || (paramObject instanceof Integer)) {
                //2.3.记录当前占位符，表示当前占位符需要进行移除
                TfParameterMappingHolder.recordRemoveParameterMapping(placeholderKey);
                //2.4 返回结果
                return Long.valueOf(paramObject.toString());
            }
        }

        //3.都不是，获取不了
        return null;
    }


    /**
     * 解析expression归属的表信息，并构建成UpstreamExpressionDto
     * 一般情况下用于将这个表达式传递给下游，但是下游又需要知道这个表达式的归属字段信息的时候使用
     * 因为到了下游可能和当前处于一层作用域，也可能处于不同层，所以这里提前解析好了再传递
     *
     * @author liutangqi
     * @date 2026/11/20 11:07
     * @Param [baseFieldParseTable, expression]
     **/
    public static UpstreamExpressionDto buildUpstreamExpressionDto(BaseFieldParseTable baseFieldParseTable, Expression expression) {
        //不是Column直接返回原表达式
        if (!(expression instanceof Column)) {
            return UpstreamExpressionDto.newInstance(expression);
        }
        ColumnTableDto columnTableDto = JsqlparserUtil.parseColumn((Column) expression, baseFieldParseTable);
        return UpstreamExpressionDto.newInstance(expression, columnTableDto);
    }

    /**
     * 解析expression归属的表信息，并构建成UpstreamExpressionDto
     * 一般情况下用于将这个表达式传递给下游，但是下游又需要知道这个表达式的归属字段信息的时候使用
     * 因为到了下游可能和当前处于一层作用域，也可能处于不同层，所以这里提前解析好了再传递
     *
     * @author liutangqi
     * @date 2026/11/20 11:07
     * @Param [baseFieldParseTable, expression]
     **/
    public static List<UpstreamExpressionDto> buildUpstreamExpressionDto(BaseFieldParseTable baseFieldParseTable, List<? extends Expression> expressions) {
        return expressions.stream().map(m -> buildUpstreamExpressionDto(baseFieldParseTable, m)).collect(Collectors.toList());
    }


    /**
     * 特殊语法处理
     * 如果insert into 后面的表没有跟具体字段的话，根据情况将字段进行补全或者不符合的话进行报错处理
     * 注意：insert set语法是不需要补全的，因为这种语法是在updateSet中处理的，是一个字段对应一个值
     *
     * @author liutangqi
     * @date 2026/11/24 15:00
     * @Param []
     **/
    public static void perfectColumn(Insert insert) {
        Table table = insert.getTable();
        //1.insert set这种语法，不涉及字段补全
        if (insert.getSelect() == null) {
            return;
        }

        //2.当前表在本项目功能中不涉及的话，则不处理
        if (!TableCache.getCurConfigTable().contains(table.getName())) {
            return;
        }

        //3.特殊语法校验
        //insert into 表  values( ?,?,?,?,?,?,?,?) 这种语法，表后面没有跟具体的字段顺序
        //下面获取到的columns 就是空的 ，如果当前配置开启了自动读取表结构的话，我们是能获取到表字段顺序的，这里可以自动填充，否则拿不到表字段顺序，我们没办法知道第几个字段进行加密处理的
        //遇到了这种语法，且未开启自动读取表结构，抛出异常，告知用户手动调整
        List<Column> columns = insert.getColumns();
        if (CollectionUtils.isEmpty(columns) && !TableCache.getCurConfig().isAutoFill()) {
            log.error("【sangsang】insert 语句未指定表字段顺序，且当前未开启自动读取表结构，无法获取表字段顺序。此情况无法处理！！！请规范语法，在表名后指定字段或开启 sangsang.autoFill=true  原sql:{}", insert.toString());
            throw new FieldException("insert 语句未指定表字段顺序，且当前未开启自动读取表结构，无法获取表字段顺序。此情况无法处理！！！请规范语法，在表名后指定字段或开启 sangsang.autoFill=true");
        }

        //4.当前配置了自动读取表结构，这种情况下才能拿到真实的字段顺序，此时根据缓存的顺序把字段补齐
        if (CollectionUtils.isEmpty(columns) && TableCache.getCurConfig().isAutoFill()) {
            List<String> fieldLinkedList = TableCache.getTableFieldMap().get(table.getName());
            columns = fieldLinkedList.stream().map(m -> ExpressionsUtil.buildColumn(m, table.getName())).collect(Collectors.toList());
            insert.setColumns(new ExpressionList(columns));
        }
    }

    /**
     * 解析insert语句的每个 column 实体类字段上存在的@FieldEncryptor
     * 注意:这里需要区别于JsqlparserUtil#needEncryptFieldEncryptor()
     * 当前语法的insert的columns是不存在别名的，所以这里直接简单从缓存中获取就行，上面的方法还存在了别名的复杂处理
     *
     * @author liutangqi
     * @date 2026/11/23 13:41
     * @Param [insert]
     **/
    public static List<FieldEncryptor> parseInsertFieldEncryptor(Table table, List<Column> columns) {
        List<FieldEncryptor> needEncryptFieldEncryptor = new ArrayList<>();
        //1 * 不处理
        if (CollectionUtils.isEmpty(columns)) {
            return needEncryptFieldEncryptor;
        }

        //2 从表结构缓存中获取insert 表的密文存储字段信息
        Map<String, FieldEncryptor> fieldEncryptMap = TableCache.getTableFieldEncryptInfo().getOrDefault(table.getName(), new FieldHashMapWrapper<>());

        //3.将每个column对应的@FieldEncryptor的关系维护到结果集中
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            //注意：因为这里是insert语句，不存在别名，所以这里直接从缓存的Map中通过字段名获取头上@FieldEncryptor即可，而不是走JsqlparserUtil.needEncryptFieldEncryptor
            FieldEncryptor leftFieldEncryptor = fieldEncryptMap.get(column.getColumnName());
            needEncryptFieldEncryptor.add(i, leftFieldEncryptor);
        }
        return needEncryptFieldEncryptor;
    }

    /**
     * 展开DML返回列中的*，并保留原返回列顺序
     * 注意：这里的* 是只包含主表table的全部字段
     *
     * @param selectItems DML语句中的返回字段列表
     * @param table       当前DML语句主表（PS：如果DML返回的是*的话，有点特殊，这里的*是只包含这个主表的字段，而不是当前作用域这层的所有字段）
     * @author liutangqi & gpt-5.6-sol
     * @date 2026/11/17 15:20
     **/
    public static List<SelectItem<?>> perfectDmlResultItems(List<SelectItem<?>> selectItems,
                                                            Table table) {
        List<SelectItem<?>> resultItems = new ArrayList<>();
        for (SelectItem<?> selectItem : selectItems) {
            Expression expression = selectItem.getExpression();
            //1.不存在*就不处理
            if (!(expression instanceof AllTableColumns) && !(expression instanceof AllColumns)) {
                resultItems.add(selectItem);
                continue;
            }

            //2.当前table不需要密文存储也不需要处理
            if (!TableCache.getFieldEncryptTable().contains(table.getName())) {
                resultItems.add(selectItem);
                continue;
            }

            //3. *转换后的字段归属的表，如果原来的字段有别名归属（别名.*），这里就有值
            AtomicReference<Table> affiliationTable = new AtomicReference<>();

            //4. 处理 别名.* 的字段归属
            if (expression instanceof AllTableColumns) {
                AllTableColumns exp = (AllTableColumns) expression;
                affiliationTable.set(exp.getTable());
            }

            //5.表结构缓存中拿到对应表字段顺序，替换*
            List<String> fieldLinkedList = TableCache.getTableFieldMap().get(table.getName());
            List<SelectItem<?>> columnsItems = fieldLinkedList.stream()
                    .map(m -> ExpressionsUtil.buildColumn(m, affiliationTable.get()))
                    .map(SelectItem::new)
                    .collect(Collectors.toList());
            resultItems.addAll(columnsItems);
        }
        return resultItems;
    }
}
