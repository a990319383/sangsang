package com.sangsang.util;

import cn.hutool.core.util.ObjectUtil;
import com.sangsang.cache.SqlParseCache;
import com.sangsang.cache.encryptor.EncryptorInstanceCache;
import com.sangsang.cache.fielddefault.FieldDefaultInstanceCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.annos.fielddefault.FieldDefault;
import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.ColumnUniqueDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.visitor.dbencrtptor.DBDecryptExpressionVisitor;
import com.sangsang.visitor.pojoencrtptor.PlaceholderExpressionVisitor;
import com.sangsang.visitor.transformation.TransformationExpressionVisitor;
import com.sangsang.visitor.transformation.wrap.ExpressionWrapper;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 解析sql语句相关的工具类
 *
 * @author liutangqi
 * @date 2024/2/1 14:14
 */
public class JsqlparserUtil {

    /**
     * Jsqlparser解析的统一入口，不直接使用原生的CCJSqlParserUtil.parse()
     *
     * @author liutangqi
     * @date 2025/6/18 14:19
     * @Param [sql]
     **/
    public static Statement parse(String sql) throws JSQLParserException {
        //1.去除空白行（Jsqlparser4.9中sql中有空白行会解析报错）
        String clearSql = StringUtils.replaceLineBreak(sql);

        //2.判断缓存是否命中，没命中解析一个扔缓存中
        Statement statement = SqlParseCache.getSqlParseCache(clearSql);
        if (statement == null) {
            statement = CCJSqlParserUtil.parse(clearSql);
            SqlParseCache.setSqlParseCache(clearSql, statement);
        }

        //3.将statement深克隆一份给到调用方，这里不能返回缓存对象，缓存对象里面会改，相互影响
        //备注：这里深克隆一份的耗时大约是重新解析一遍的十分之一，所以这里是个有效缓存，这个也是个高频操作，整个系统多种功能都强依赖这个
        return ObjectUtil.cloneByStream(statement);
    }

    /**
     * 补齐 select * 的所有字段
     * 备注：只补齐有实体类的表，并且这个实体类存在需要加密字段
     *
     * @author liutangqi
     * @date 2024/2/19 17:20
     * @Param [selectItem, layerFieldTableMap 当前层的表拥有的全部字段 ]
     **/
    public static List<SelectItem> perfectAllColumns(SelectItem selectItem, Map<String, Set<FieldInfoDto>> layerFieldTableMap) {
        Expression expression = selectItem.getExpression();

        //1. select 别名.*  （注意：AllColumns AllTableColumns 有继承关系，这里判断顺序不能改）
        if (expression instanceof AllTableColumns) {
            String tableName = ((AllTableColumns) expression).getTable().getName();
            Set<FieldInfoDto> fieldInfoSet = layerFieldTableMap.get(tableName);
            //1.1没有配置此表的字段信息，返回原表达式
            if (CollectionUtils.isEmpty(fieldInfoSet)) {
                return Arrays.asList(selectItem);
            }
            //1.2 配置了此表，此表是个虚拟表或此表不需要密文存储，则保持原样
            FieldInfoDto fieldInfoDto = new ArrayList<FieldInfoDto>(fieldInfoSet).get(0);
            if (!fieldInfoDto.isFromSourceTable() || !TableCache.getFieldEncryptTable().contains(fieldInfoDto.getSourceTableName())) {
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
            for (Map.Entry<String, Set<FieldInfoDto>> fieldInfoEntry : layerFieldTableMap.entrySet()) {
                //2.1 此表字段没有配置拥有哪些字段，则使用 别名.*
                if (CollectionUtils.isEmpty(fieldInfoEntry.getValue())) {
                    SelectItem<?> item = SelectItem.from(new AllTableColumns(new Table(fieldInfoEntry.getKey())));
                    selectItems.add(item);
                }
                //2.2 此表如果配置的有，此表是个虚拟表或这张表不需要密文存储，则也返回别名.*
                else if (!new ArrayList<FieldInfoDto>(fieldInfoEntry.getValue()).get(0).isFromSourceTable() || !TableCache.getFieldEncryptTable().contains(new ArrayList<FieldInfoDto>(fieldInfoEntry.getValue()).get(0).getSourceTableName())) {
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
     * 解析当前字段所属表的信息
     *
     * @author liutangqi
     * @date 2024/3/6 14:52
     * @Param [column, layer, layerFieldTableMap 每一层的表拥有的全部字段的map]
     **/
    public static ColumnTableDto parseColumn(Column column, int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        //字段名
        String columName = column.getColumnName();
        //字段所属表 （只有select 别名.字段名 时这个才有值，其它的为null）
        Table table = column.getTable();

        //字段所属的表的别名(from 后面接的表的别名)
        AtomicReference<String> tableAliasName = new AtomicReference<>();
        //字段所属表的真实表的名字
        AtomicReference<String> sourceTableName = new AtomicReference<>();
        //字段所属真实字段名
        AtomicReference<String> sourceColumn = new AtomicReference<>();
        //字段所属表的真实名字 from 后面的表的名字 （tableAliasName的真实名字）
        AtomicBoolean fromSourceTable = new AtomicBoolean(false);


        //1.没有指定表名时，从当前层的表的所有字段里面找到这个名字的表( select 字段)
        if (table == null) {
            layerFieldTableMap.getOrDefault(String.valueOf(layer), new FieldHashMapWrapper<>()).entrySet().forEach(f -> {
                List<FieldInfoDto> matchFields = f.getValue().stream().filter(fi -> StringUtils.fieldEquals(fi.getColumnName(), columName)).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(matchFields)) {
                    //当前层的所有字段里面叫这个的，正确sql语法中只会有一个，所以get(0)
                    FieldInfoDto matchField = matchFields.get(0);
                    sourceTableName.set(matchField.getSourceTableName());
                    sourceColumn.set(matchField.getSourceColumn());
                    fromSourceTable.set(matchField.isFromSourceTable());
                    tableAliasName.set(f.getKey());
                }
            });
        }

        //2.有指定表名时，从当前层的这张表的所有字段里面这个字段的信息 （select 别名.字段）
        if (table != null) {
            String columnTableName = table.getName();
            List<FieldInfoDto> matchFields = Optional.ofNullable(layerFieldTableMap.get(String.valueOf(layer)).get(columnTableName)).orElse(new HashSet<>()).stream().filter(f -> StringUtils.fieldEquals(f.getColumnName(), columName)).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(matchFields)) {
                //当前层的所有字段里面叫这个的，正确sql语法中只会有一个，所以get(0)
                FieldInfoDto matchField = matchFields.get(0);
                sourceTableName.set(matchField.getSourceTableName());
                sourceColumn.set(matchField.getSourceColumn());
                fromSourceTable.set(matchField.isFromSourceTable());
                tableAliasName.set(columnTableName);
            }
        }

        return ColumnTableDto.builder().tableAliasName(tableAliasName.get()).sourceTableName(sourceTableName.get()).sourceColumn(sourceColumn.get()).fromSourceTable(fromSourceTable.get()).build();
    }


    /**
     * 判断当前column 是否是表字段还是常量
     * 主要用于语法转换时 ` " ‘ 符号的取舍
     * 当这个方法返回的是true时，说明这个字段不是常量，可能来自某张虚拟表或者真实表的字段
     * 注意：来自虚拟表的字段，哪怕这个字段在原虚拟表中属于常量，这个也是表字段
     *
     * @author liutangqi
     * @date 2025/5/27 13:08
     * @Param [column, layer, layerFieldTableMap]
     **/
    public static boolean isTableFiled(Column column, int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        //字段名
        String columName = column.getColumnName();
        //字段所属表 （只有select 别名.字段名 时这个才有值，其它的为null）
        Table table = column.getTable();

        //1.没有指定表名时，从当前层的表的所有字段里面找到这个名字的表( select 字段)
        if (table == null) {
            for (Map.Entry<String, Set<FieldInfoDto>> entry : layerFieldTableMap.getOrDefault(String.valueOf(layer), new FieldHashMapWrapper<>()).entrySet()) {
                //任意的表有一个字段符合，则返回true，表示是个表字段
                if (entry.getValue().stream().anyMatch(m -> StringUtils.fieldEquals(m.getColumnName(), columName))) {
                    return true;
                }
            }
        }

        //2.有指定表名时，说明肯定来自某张表，哪怕是虚拟表
        return table != null;
    }

    /**
     * 判断当前column 是否需要加解密
     *
     * @author liutangqi
     * @date 2024/3/11 15:28
     * @Param [column, layer, layerFieldTableMap]
     **/
    public static boolean needEncrypt(Column column, int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        //1.匹配所属表信息
        ColumnTableDto columnTableDto = parseColumn(column, layer, layerFieldTableMap);

        //2.当前字段不需要解密直接返回 (实体类上面没有标注@FieldEncryptor注解 或者字段不是来源自真实表)
        return columnTableDto.isFromSourceTable() && Optional.ofNullable(TableCache.getTableFieldEncryptInfo()).map(m -> m.get(columnTableDto.getSourceTableName())).map(m -> m.get(columnTableDto.getSourceColumn())).orElse(null) != null;
    }

    /**
     * 判断这个字段是否需要密文存储，需要的话，返回字段标注的@FieldEncryptor
     * 参考上面这个方法 com.sangsang.util.JsqlparserUtil#needEncrypt(net.sf.jsqlparser.schema.Column, int, java.util.Map)
     *
     * @author liutangqi
     * @date 2025/6/25 16:30
     * @Param [column, layer, layerFieldTableMap]
     **/
    public static FieldEncryptor needEncryptFieldEncryptor(Expression expression, int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        //0.不是Column直接返回，其它表达式的话上面是不可能标识得有注解的
        if (!(expression instanceof Column)) {
            return null;
        }

        //1.匹配所属表信息
        Column column = (Column) expression;
        ColumnTableDto columnTableDto = parseColumn(column, layer, layerFieldTableMap);

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
    public static void putFieldInfo(Map<String, Map<String, Set<FieldInfoDto>>> layerTableMap, int layer, String tableName, FieldInfoDto dto) {
        Map<String, Set<FieldInfoDto>> layerFieldMap = Optional.ofNullable(layerTableMap.get(String.valueOf(layer))).orElse(new FieldHashMapWrapper<>());
        Set<FieldInfoDto> fieldInfoDtos = Optional.ofNullable(layerFieldMap.get(tableName)).orElse(new HashSet<>());

        fieldInfoDtos.add(dto);
        layerFieldMap.put(tableName, fieldInfoDtos);
        layerTableMap.put(String.valueOf(layer), layerFieldMap);
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
    public static void putFieldInfo(Map<String, Map<String, Set<FieldInfoDto>>> layerTableMap, int layer, String tableName, Set<FieldInfoDto> dtos) {
        Map<String, Set<FieldInfoDto>> layerFieldMap = Optional.ofNullable(layerTableMap.get(String.valueOf(layer))).orElse(new FieldHashMapWrapper<>());
        Set<FieldInfoDto> fieldInfoDtos = Optional.ofNullable(layerFieldMap.get(tableName)).orElse(new HashSet<>());

        fieldInfoDtos.addAll(dtos);
        layerFieldMap.put(tableName, fieldInfoDtos);
        layerTableMap.put(String.valueOf(layer), layerFieldMap);
    }


    /**
     * 解析出newMap 比 oldMap 多的元素
     * 注意：这里的key的判断还是遵循 当前的大小写敏感和当前的关键字符号的配置
     *
     * @author liutangqi
     * @date 2024/3/20 13:54
     * @Param [oldMap, newMap]
     **/
    public static Map<String, Set<FieldInfoDto>> parseNewlyIncreased(Map<String, Set<FieldInfoDto>> oldMap, Map<String, Set<FieldInfoDto>> newMap) {
        Map<String, Set<FieldInfoDto>> result = new FieldHashMapWrapper<>();
        for (Map.Entry<String, Set<FieldInfoDto>> newMapEntry : newMap.entrySet()) {
            //key 旧的没有，直接整个都是新增的
            if (!oldMap.containsKey(newMapEntry.getKey())) {
                result.put(newMapEntry.getKey(), newMapEntry.getValue());
                //key有，筛选出Set中新增的
            } else {
                Set<FieldInfoDto> newlyIncreasedSet = newMapEntry.getValue().stream().filter(f -> !oldMap.get(newMapEntry.getKey()).contains(f)).collect(Collectors.toSet());
                if (CollectionUtils.isNotEmpty(newlyIncreasedSet)) {
                    result.put(newMapEntry.getKey(), newlyIncreasedSet);
                }
            }
        }
        return result;
    }

    /**
     * 根据当前sql的查询字段和表拥有字段的集合，判断当前sql是否存在需要加密的表
     *
     * @author liutangqi
     * @date 2024/3/20 15:08
     * @Param [layerSelectTableFieldMap, layerFieldTableMap]
     **/
    public static boolean needEncrypt(Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        Set<FieldInfoDto> selectFieldInfoDtos = layerSelectTableFieldMap.values().stream().flatMap(f -> f.values().stream()).flatMap(Collection::stream).filter(f -> TableCache.getFieldEncryptTable().contains(f.getSourceTableName())).collect(Collectors.toSet());
        Set<FieldInfoDto> fieldInfoDtos = layerFieldTableMap.values().stream().flatMap(f -> f.values().stream()).flatMap(Collection::stream).filter(f -> TableCache.getFieldEncryptTable().contains(f.getSourceTableName())).collect(Collectors.toSet());
        return CollectionUtils.isNotEmpty(selectFieldInfoDtos) || CollectionUtils.isNotEmpty(fieldInfoDtos);
    }

    /**
     * 根据表和字段信息，从缓存中找到对应字段上面标注的注解
     *
     * @author liutangqi
     * @date 2024/7/24 15:24
     * @Param [dto]
     **/
    public static FieldEncryptor parseFieldEncryptor(ColumnTableDto dto) {
        Map<String, FieldEncryptor> stringFieldEncryptorMap = Optional.ofNullable(TableCache.getTableFieldEncryptInfo().get(dto.getSourceTableName())).orElse(new FieldHashMapWrapper<>());
        return stringFieldEncryptorMap.get(dto.getSourceColumn());
    }

    /**
     * 如果表达式的一边是Column 一边是我们的特殊表达式，则将他们的对应关系维护到placeholderColumnTableMap 中
     *
     * @author liutangqi
     * @date 2024/7/11 11:24
     * @Param [layer 当前层数, layerFieldTableMap 当前层所有的字段信息,expression 表达式, placeholderColumnTableMap 存放结果集的map]
     **/
    public static void parseWhereColumTable(int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap, BinaryExpression expression, Map<String, ColumnTableDto> placeholderColumnTableMap) {
        Expression leftExpression = expression.getLeftExpression();
        Expression rightExpression = expression.getRightExpression();

        parseWhereColumTable(layer, layerFieldTableMap, leftExpression, rightExpression, placeholderColumnTableMap);
    }


    /**
     * 如果表达式的一边是Column 一边是我们的特殊表达式，则将他们的对应关系维护到placeholderColumnTableMap 中
     *
     * @author liutangqi
     * @date 2024/7/11 11:24
     * @Param [layer 当前层数, layerFieldTableMap 当前层所有的字段信息,leftExpression 左表达式,rightExpression右表达式, placeholderColumnTableMap 存放结果集的map]
     **/
    public static void parseWhereColumTable(int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap, Expression leftExpression, Expression rightExpression, Map<String, ColumnTableDto> placeholderColumnTableMap) {
        //左边是列，右边是我们的占位符
        if (leftExpression instanceof Column && rightExpression != null && rightExpression.toString().contains(FieldConstant.PLACEHOLDER)) {
            ColumnTableDto columnTableDto = JsqlparserUtil.parseColumn((Column) leftExpression, layer, layerFieldTableMap);
            placeholderColumnTableMap.put(rightExpression.toString(), columnTableDto);
        }

        //左边是我们的占位符 右边是列
        if (rightExpression instanceof Column && leftExpression != null && leftExpression.toString().contains(FieldConstant.PLACEHOLDER)) {
            ColumnTableDto columnTableDto = JsqlparserUtil.parseColumn((Column) rightExpression, layer, layerFieldTableMap);
            placeholderColumnTableMap.put(leftExpression.toString(), columnTableDto);
        }
    }

    /**
     * 根据sql当前层的字段，过滤出其中需要设置变动默认值的字段信息
     *
     * @author liutangqi
     * @date 2025/7/17 11:26
     * @Param [someLayerFieldTableMap: 某一层的字段信息]
     **/
    @Deprecated
    public static Map<ColumnUniqueDto, FieldDefault> filterFieldDefault(Map<String, Set<FieldInfoDto>> someLayerFieldTableMap, SqlCommandEnum sqlCommandEnum) {
        //1.创建处理结果的容器
        Map<ColumnUniqueDto, FieldDefault> res = new HashMap<>();

        //2.当前项目需要设置默认值的表名
        Map<String, Map<String, FieldDefault>> tableFieldDefaultInfo = TableCache.getTableFieldDefaultInfo();

        //3.依次处理每张表信息
        for (Map.Entry<String, Set<FieldInfoDto>> fieldMapEntry : someLayerFieldTableMap.entrySet()) {
            String tableAliasName = fieldMapEntry.getKey();
            for (FieldInfoDto fieldInfoDto : fieldMapEntry.getValue()) {
                //获取此字段上面标注的@FieldDefault，存在注解并且当前需要处理 就放结果集里面
                Optional.ofNullable(tableFieldDefaultInfo.get(fieldInfoDto.getSourceTableName()))
                        .map(m -> m.get(fieldInfoDto.getSourceColumn()))
                        .filter(f -> FieldDefaultInstanceCache.getInstance(f.value()).whetherToHandle(sqlCommandEnum))
                        .ifPresent(p -> res.put(ColumnUniqueDto.builder().tableAliasName(tableAliasName).sourceColumn(fieldInfoDto.getSourceColumn()).build(), p));
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
            FieldEncryptor leftFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(expression.getLeftExpression(), dbExpressionVisitor.getLayer(), dbExpressionVisitor.getLayerFieldTableMap());
            FieldEncryptor rightFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(expression.getRightExpression(), dbExpressionVisitor.getLayer(), dbExpressionVisitor.getLayerFieldTableMap());
            if (Objects.equals(Optional.ofNullable(leftFieldEncryptor).map(FieldEncryptor::value).orElse(null), Optional.ofNullable(rightFieldEncryptor).map(FieldEncryptor::value).orElse(null))) {
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
            FieldEncryptor leftColumnFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(expression.getLeftExpression(), dbExpressionVisitor.getLayer(), dbExpressionVisitor.getLayerFieldTableMap());
            if (leftColumnFieldEncryptor != null) {
                Expression newRightExpression = EncryptorInstanceCache.<Expression>getInstance(leftColumnFieldEncryptor.value()).encryption(expression.getRightExpression());
                expression.setRightExpression(newRightExpression);
            }
            return;
        }

        //3. 左边不是Column 右边是 Column  ，避免索引失效，将非Column进行加密处理即可
        if ((expression.getRightExpression() instanceof Column) && !(expression.getLeftExpression() instanceof Column)) {
            //Column 是需要加密的字段则将非Column进行加密
            FieldEncryptor rightColumnFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(expression.getRightExpression(), dbExpressionVisitor.getLayer(), dbExpressionVisitor.getLayerFieldTableMap());
            if (rightColumnFieldEncryptor != null) {
                Expression newLeftExpression = EncryptorInstanceCache.<Expression>getInstance(rightColumnFieldEncryptor.value()).encryption(expression.getLeftExpression());
                expression.setLeftExpression(newLeftExpression);
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
            //1.需要设置默认值的字段原sql就存在，并且开启了强制覆盖 则将默认值给原sql用if的方式给替换了
            if (i < expressions.size() && upstreamFieldDefaultColumns.get(i) != null && upstreamFieldDefaultColumns.get(i).mandatoryOverride()) {
                Expression fieldDefaultExp = ExpressionsUtil.buildFieldDefaultExp(upstreamFieldDefaultColumns.get(i).value());
                Function ifFunction = ExpressionsUtil.buildAffirmativeIf(expressions.get(i), fieldDefaultExp);
                expressions.set(i, ifFunction);
            }

            //2.需要设置默认值的字段原sql不存在，则新增
            if (i >= expressions.size() && upstreamFieldDefaultColumns.get(i) != null) {
                Expression fieldDefaultExp = ExpressionsUtil.buildFieldDefaultExp(upstreamFieldDefaultColumns.get(i).value());
                expressions.add(i, fieldDefaultExp);
            }
        }
        return expressions;
    }
}
