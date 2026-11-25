package com.sangsang.util.visitor;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.dto.*;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.pojoencrtptor.PlaceholderExpressionVisitor;
import com.sangsang.visitor.pojoencrtptor.PlaceholderSelectFromItemVisitor;
import com.sangsang.visitor.pojoencrtptor.PlaceholderSelectVisitor;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.*;
import java.util.stream.Collectors;

/**
 * pojo模式访问者工具类
 *
 * @author liutangqi
 * @date 2026/10/29 10:16
 */
public class EncryptorPojoVisitorUtil {


    /**
     * 如果表达式的一边是Column 一边是我们的特殊表达式，则将他们的对应关系维护到placeholderColumnTableMap 中
     *
     * @author liutangqi
     * @date 2024/7/11 11:24
     * @Param [layer 当前层数, layerFieldTableMap 当前层所有的字段信息,expression 表达式, placeholderColumnTableMap 存放结果集的map]
     **/
    public static void parseWhereColumTable(BaseFieldParseTable baseFieldParseTable, BinaryExpression expression, Map<String, ColumnTableDto> placeholderColumnTableMap) {
        Expression leftExpression = expression.getLeftExpression();
        Expression rightExpression = expression.getRightExpression();

        EncryptorPojoVisitorUtil.parseWhereColumTable(baseFieldParseTable, leftExpression, rightExpression, placeholderColumnTableMap);
    }

    /**
     * 将左右表达式的对应关系维护到placeholderColumnTableMap 中
     *
     * @author liutangqi
     * @date 2026/11/20 13:57
     * @Param [layer 当前层数, layerFieldTableMap 当前层所有的字段信息,leftExpression 左表达式,rightExpression右表达式, placeholderColumnTableMap 存放结果集的map]
     **/
    public static void parseWhereColumTable(BaseFieldParseTable baseFieldParseTable,
                                            Expression leftExpression,
                                            Expression rightExpression,
                                            Map<String, ColumnTableDto> placeholderColumnTableMap) {
        //1.异常数据过滤
        if (leftExpression == null || rightExpression == null) {
            return;
        }
        //2.构建成字段匹配后的表达式
        UpstreamExpressionDto leftExpressionDto = JsqlparserUtil.buildUpstreamExpressionDto(baseFieldParseTable, leftExpression);
        UpstreamExpressionDto rightExpressionDto = JsqlparserUtil.buildUpstreamExpressionDto(baseFieldParseTable, rightExpression);
        //3.走真正解析的逻辑
        EncryptorPojoVisitorUtil.parseWhereColumTable(baseFieldParseTable, leftExpressionDto, rightExpressionDto, placeholderColumnTableMap);
    }

    /**
     * 将左右表达式的对应关系维护到placeholderColumnTableMap 中
     *
     * @author liutangqi
     * @date 2024/7/11 11:24
     * @Param [layer 当前层数, layerFieldTableMap 当前层所有的字段信息,leftExpression 左表达式,rightExpression右表达式, placeholderColumnTableMap 存放结果集的map]
     **/
    public static void parseWhereColumTable(BaseFieldParseTable baseFieldParseTable,
                                            UpstreamExpressionDto leftExpression,
                                            UpstreamExpressionDto rightExpression,
                                            Map<String, ColumnTableDto> placeholderColumnTableMap) {
        //1.异常数据过滤
        if (leftExpression == null || rightExpression == null) {
            return;
        }

        //2.如果左表达式是 ? 或者 Column 则将左表达式作为上游对应字段，去解析右表达式
        if (leftExpression.getUpstreamExpression() instanceof JdbcParameter || leftExpression.getUpstreamExpression() instanceof Column) {
            PlaceholderExpressionVisitor rightExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(baseFieldParseTable, placeholderColumnTableMap, leftExpression);
            rightExpression.getUpstreamExpression().accept(rightExpressionVisitor);
        }

        //3.如果右表达式是 ? 或者 Column 则将右表达式作为上游对应字段，去解析左表达式
        else if (rightExpression.getUpstreamExpression() instanceof JdbcParameter || rightExpression.getUpstreamExpression() instanceof Column) {
            PlaceholderExpressionVisitor leftExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(baseFieldParseTable, placeholderColumnTableMap, rightExpression);
            leftExpression.getUpstreamExpression().accept(leftExpressionVisitor);
        }
    }

    /**
     * 将当前sql的最外层查询的字段，构建成List<FieldEncryptorInfoDto>的格式
     *
     * @author liutangqi
     * @date 2026/11/25 14:18
     * @Param [baseFieldParseTable]
     **/
    public static List<FieldEncryptorInfoDto> buildFirstLayerSelectTableFieldMap(BaseFieldParseTable baseFieldParseTable) {
        //1. 获取最外层的所有查询字段
        Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap = baseFieldParseTable.getLayerSelectTableFieldMap();
        List<FieldInfoDto> selectFiles = layerSelectTableFieldMap.getOrDefault(NumberConstant.ONE, new FieldHashMapWrapper<>())
                .values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        //2.将每个字段从实体类上找到标注的@FieldEncryptor 注解，并构建返回对象
        return selectFiles.stream()
                .map(m -> FieldEncryptorInfoDto.builder()
                        .columnName(m.getColumnName())
                        .sourceColumn(m.getSourceColumn())
                        .sourceTableName(m.getSourceTableName())
                        .fieldEncryptor(TableCache.getTableFieldEncryptInfo().getOrDefault(m.getSourceTableName(),
                                        new FieldHashMapWrapper<>())
                                .get(m.getSourceColumn()))
                        .build()
                ).collect(Collectors.toList());
    }


    /**
     * 解析updateSet中字段和占位符的对应关系
     *
     * @author liutangqi
     * @date 2026/10/28 16:00
     * @Param [updateSets, fieldParseTable]
     **/
    public static void updateSets(List<UpdateSet> updateSets,
                                  BaseFieldParseTable baseFieldParseTable,
                                  Map<String, ColumnTableDto> placeholderColumnTableMap) {
        if (CollectionUtils.isEmpty(updateSets)) {
            return;
        }
        for (UpdateSet updateSet : updateSets) {
            if (CollectionUtils.isEmpty(updateSet.getValues())) {
                continue;
            }
            //处理 update set(xxx,xxx) = (select xxx,xxx from) 这种子查询
            if (updateSet.getValues().size() == 1 && updateSet.getValues().get(0) instanceof ParenthesedSelect) {
                ParenthesedSelect parenthesedSelect = (ParenthesedSelect) updateSet.getValues().get(0);
                List<UpstreamExpressionDto> upstreamExpressionDtos = updateSet.getColumns().stream().map(m -> JsqlparserUtil.buildUpstreamExpressionDto(baseFieldParseTable, m)).collect(Collectors.toList());
                PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(baseFieldParseTable, placeholderColumnTableMap, upstreamExpressionDtos);
                parenthesedSelect.accept(placeholderSelectVisitor);
            }
            //其它普通语法，一次将 Column和Value做对应即可
            else {
                EncryptorPojoVisitorUtil.columnsAndValues(updateSet.getColumns(), updateSet.getValues(), baseFieldParseTable, placeholderColumnTableMap);
            }
        }
    }

    /**
     * 解析字段列表和值列表中字段和占位符的对应关系
     *
     * @author liutangqi
     * @date 2026/10/29 11:15
     * @Param [columns, expressions, fieldParseTable, placeholderColumnTableMap]
     **/
    public static void columnsAndValues(List<? extends Column> columns,
                                        List<? extends Expression> expressions,
                                        BaseFieldParseTable baseFieldParseTable,
                                        Map<String, ColumnTableDto> placeholderColumnTableMap) {
        //字段列表和值列表长度必须对应
        if (CollectionUtils.isEmpty(columns) || CollectionUtils.isEmpty(expressions) || columns.size() != expressions.size()) {
            return;
        }
        for (int i = 0; i < columns.size(); i++) {
            EncryptorPojoVisitorUtil.parseWhereColumTable(baseFieldParseTable,
                    columns.get(i),
                    expressions.get(i),
                    placeholderColumnTableMap);
        }
    }

    /**
     * 解析条件表达式中的占位符
     *
     * @author liutangqi
     * @date 2026/10/29 11:20
     * @Param [expression, fieldParseTable, placeholderColumnTableMap]
     **/
    public static void expression(Expression expression,
                                  BaseFieldParseTable baseFieldParseTable,
                                  Map<String, ColumnTableDto> placeholderColumnTableMap) {
        if (expression == null) {
            return;
        }
        PlaceholderExpressionVisitor expressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(baseFieldParseTable, placeholderColumnTableMap);
        expression.accept(expressionVisitor);
    }

    /**
     * 解析join涉及的表及on条件中的占位符
     *
     * @author liutangqi
     * @date 2026/10/29 11:25
     * @Param [joins, fieldParseTable, placeholderColumnTableMap]
     **/
    public static void joins(List<Join> joins,
                             BaseFieldParseTable baseFieldParseTable,
                             Map<String, ColumnTableDto> placeholderColumnTableMap) {
        if (CollectionUtils.isEmpty(joins)) {
            return;
        }
        PlaceholderSelectFromItemVisitor placeholderSelectFromItemVisitor = PlaceholderSelectFromItemVisitor.newInstanceCurLayer(baseFieldParseTable, placeholderColumnTableMap);
        for (Join join : joins) {
            //1.解析join右表子查询中的占位符
            FromItem rightItem = join.getRightItem();
            if (rightItem != null) {
                rightItem.accept(placeholderSelectFromItemVisitor);
            }

            //2.解析join的on条件中的占位符
            if (CollectionUtils.isEmpty(join.getOnExpressions())) {
                continue;
            }
            for (Expression onExpression : join.getOnExpressions()) {
                EncryptorPojoVisitorUtil.expression(onExpression, baseFieldParseTable, placeholderColumnTableMap);
            }
        }
    }

    /**
     * 处理 CTE 语法里面的占位符信息
     *
     * @author liutangqi
     * @date 2026/10/29 17:52
     * @Param [withItemsList, placeholderSelectVisitor]
     **/
    public static void cte(List<WithItem> withItemsList, PlaceholderSelectVisitor placeholderSelectVisitor) {
        if (CollectionUtils.isEmpty(withItemsList)) {
            return;
        }
        for (WithItem withItem : withItemsList) {
            withItem.accept(placeholderSelectVisitor);
        }
    }


    /**
     * 通过上游的表达式列表和当前表达式列表，解析对应占位符的对应关系
     *
     * @author liutangqi
     * @date 2026/11/3 14:54
     * @Param [upstreamExpressionList, expressionList]
     **/
    public static void expressionList(PlaceholderFieldParseTable placeholderFieldParseTable,
                                      List<UpstreamExpressionDto> upstreamExpressionList,
                                      ExpressionList<Expression> expressionList) {
        if (CollectionUtils.isEmpty(upstreamExpressionList) || CollectionUtils.isEmpty(expressionList) || upstreamExpressionList.size() != expressionList.size()) {
            return;
        }
        for (int i = 0; i < expressionList.size(); i++) {
            Expression curExp = expressionList.get(i);
            UpstreamExpressionDto curExpressionDto = JsqlparserUtil.buildUpstreamExpressionDto(placeholderFieldParseTable, curExp);
            UpstreamExpressionDto upstreamExpression = upstreamExpressionList.get(i);
            EncryptorPojoVisitorUtil.parseWhereColumTable(placeholderFieldParseTable,
                    upstreamExpression,
                    curExpressionDto,
                    placeholderFieldParseTable.getPlaceholderColumnTableMap());
        }
    }


}
