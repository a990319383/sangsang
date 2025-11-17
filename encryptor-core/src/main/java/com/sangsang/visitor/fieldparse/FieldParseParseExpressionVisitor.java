package com.sangsang.visitor.fieldparse;

import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.StringUtils;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.conditional.XorExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 解析sql select中出现过的字段及所属真实表
 *
 * @author liutangqi
 * @date 2024/3/5 14:58
 */
public class FieldParseParseExpressionVisitor extends BaseFieldParseTable implements ExpressionVisitor {
    /**
     * 当前字段拥有的别名
     */
    private Alias alias;

    /**
     * 获取当前层实例对象
     *
     * @author liutangqi
     * @date 2025/3/4 17:29
     * @Param [baseFieldParseTable, alias]
     **/
    public static FieldParseParseExpressionVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable, Alias alias) {
        return new FieldParseParseExpressionVisitor(alias,
                baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap()
        );
    }

    private FieldParseParseExpressionVisitor(Alias alias, int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
        this.alias = alias;
    }


    @Override
    public void visit(BitwiseRightShift aThis) {

    }

    @Override
    public void visit(BitwiseLeftShift aThis) {

    }

    @Override
    public void visit(NullValue nullValue) {

    }

    @Override
    public void visit(Function function) {
        //别名如果不存在的话，就用Function的ToString的结果作为别名
        alias = Optional.ofNullable(alias).orElse(new Alias(SymbolConstant.FLOAT + function.toString() + SymbolConstant.FLOAT));

        //将这个别名的字段归属在 FieldConstant.FUNCTION_TMP 这张虚拟的表别名中
        //有些嵌套查询时会有* ，* 时需要包含此处理结果，所以需要把这个维护进去（所以搜索DecryptConstant.FUNCTION_TMP 这个key值没有其它取的地方，因为是*的时候用到，不需要key）
        String aliasColumName = alias.getName();
        //function处理后的结果，放的结果的key是这个
        String tableAliasName = FieldConstant.FUNCTION_TMP;

        FieldInfoDto fieldInfoDto = FieldInfoDto.builder().columnName(aliasColumName).sourceTableName(null).sourceColumn(null).fromSourceTable(false).build();

        //将当前字段存入layerSelectTableFieldMap 中
        JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), tableAliasName, fieldInfoDto);
    }

    @Override
    public void visit(SignedExpression signedExpression) {

    }

    @Override
    public void visit(JdbcParameter jdbcParameter) {

    }

    @Override
    public void visit(JdbcNamedParameter jdbcNamedParameter) {

    }

    @Override
    public void visit(DoubleValue doubleValue) {

    }

    @Override
    public void visit(LongValue longValue) {

    }

    @Override
    public void visit(HexValue hexValue) {

    }

    @Override
    public void visit(DateValue dateValue) {

    }

    @Override
    public void visit(TimeValue timeValue) {

    }

    @Override
    public void visit(TimestampValue timestampValue) {

    }

    @Override
    public void visit(Parenthesis parenthesis) {

    }

    /**
     * 当查询的字段属于常量时，将这个常量存入layerSelectTableFieldMap中
     * 归属在临时表中
     *
     * @author liutangqi
     * @date 2025/5/28 9:25
     * @Param [stringValue]
     **/
    @Override
    public void visit(StringValue stringValue) {
        //当前字段别名，别名没有取字符串名字
        String aliasColumName = Optional.ofNullable(alias).map(Alias::getName).orElse(stringValue.getValue());
        FieldInfoDto fieldInfoDto = FieldInfoDto.builder().columnName(aliasColumName).sourceTableName(null).sourceColumn(null).fromSourceTable(false).build();
        JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), FieldConstant.FUNCTION_TMP, fieldInfoDto);
    }

    @Override
    public void visit(Addition addition) {

    }

    @Override
    public void visit(Division division) {

    }

    @Override
    public void visit(IntegerDivision division) {

    }

    @Override
    public void visit(Multiplication multiplication) {

    }

    @Override
    public void visit(Subtraction subtraction) {

    }

    @Override
    public void visit(AndExpression andExpression) {

    }

    @Override
    public void visit(OrExpression orExpression) {

    }

    @Override
    public void visit(XorExpression xorExpression) {

    }

    @Override
    public void visit(Between between) {

    }

    @Override
    public void visit(OverlapsCondition overlapsCondition) {

    }

    @Override
    public void visit(EqualsTo equalsTo) {

    }

    @Override
    public void visit(GreaterThan greaterThan) {

    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {

    }

    @Override
    public void visit(InExpression inExpression) {

    }

    @Override
    public void visit(FullTextSearch fullTextSearch) {

    }

    @Override
    public void visit(IsNullExpression isNullExpression) {

    }

    @Override
    public void visit(IsBooleanExpression isBooleanExpression) {

    }

    @Override
    public void visit(LikeExpression likeExpression) {

    }

    @Override
    public void visit(MinorThan minorThan) {

    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {

    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {

    }

    @Override
    public void visit(DoubleAnd doubleAnd) {

    }

    @Override
    public void visit(Contains contains) {

    }

    @Override
    public void visit(ContainedBy containedBy) {

    }

    @Override
    public void visit(ParenthesedSelect parenthesedSelect) {

    }

    @Override
    public void visit(Column tableColumn) {
        //1.解析当前字段所属的表信息
        ColumnTableDto columnTableDto = JsqlparserUtil.parseColumn(tableColumn, this.getLayer(), this.getLayerFieldTableMap());
        //当前字段别名，别名没有取库字段名
        String aliasColumName = Optional.ofNullable(alias).map(Alias::getName).orElse(tableColumn.getColumnName());

        //2.匹配到了真实表名，则将此字段存入 layerSelectTableFieldMap
        if (StringUtils.isNotBlank(columnTableDto.getSourceTableName())) {
            FieldInfoDto fieldInfoDto = FieldInfoDto.builder().columnName(aliasColumName).sourceTableName(columnTableDto.getSourceTableName()).sourceColumn(columnTableDto.getSourceColumn()).fromSourceTable(columnTableDto.isFromSourceTable()).build();

            //将此字段存入 layerSelectTableFieldMap 中
            JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), columnTableDto.getTableAliasName(), fieldInfoDto);
        }
        //3.未匹配到真实表名，但是存在所属表别名，说明这个字段是属于内层嵌套的常量字段
        else if (StringUtils.isNotBlank(columnTableDto.getTableAliasName())) {
            FieldInfoDto fieldInfoDto = FieldInfoDto.builder().columnName(aliasColumName).sourceTableName(null).sourceColumn(null).fromSourceTable(false).build();
            JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), columnTableDto.getTableAliasName(), fieldInfoDto);
        }
        //4.如果当前字段没有匹配到真实表名，则此字段可能是个常量，这个时候把这个字段挂虚拟表上去，存到layerSelectTableFieldMap 中
        else {
            FieldInfoDto fieldInfoDto = FieldInfoDto.builder().columnName(aliasColumName).sourceTableName(null).sourceColumn(null).fromSourceTable(false).build();
            JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), FieldConstant.FUNCTION_TMP, fieldInfoDto);
        }

    }

    @Override
    public void visit(CaseExpression caseExpression) {
        // 解析当前sql的查询字段不用管 case
    }

    @Override
    public void visit(WhenClause whenClause) {
        // 解析当前sql的查询字段不用管 when
    }

    @Override
    public void visit(ExistsExpression existsExpression) {
        // 解析当前sql的查询字段不用管 exists
    }

    @Override
    public void visit(MemberOfExpression memberOfExpression) {

    }


    @Override
    public void visit(AnyComparisonExpression anyComparisonExpression) {

    }

    @Override
    public void visit(Concat concat) {

    }

    @Override
    public void visit(Matches matches) {

    }

    @Override
    public void visit(BitwiseAnd bitwiseAnd) {

    }

    @Override
    public void visit(BitwiseOr bitwiseOr) {

    }

    @Override
    public void visit(BitwiseXor bitwiseXor) {

    }

    @Override
    public void visit(CastExpression cast) {

    }

    @Override
    public void visit(Modulo modulo) {

    }

    @Override
    public void visit(AnalyticExpression aexpr) {

    }

    @Override
    public void visit(ExtractExpression eexpr) {

    }

    @Override
    public void visit(IntervalExpression iexpr) {

    }

    @Override
    public void visit(OracleHierarchicalExpression oexpr) {

    }

    @Override
    public void visit(RegExpMatchOperator rexpr) {

    }

    @Override
    public void visit(JsonExpression jsonExpr) {

    }

    @Override
    public void visit(JsonOperator jsonExpr) {

    }

    @Override
    public void visit(UserVariable var) {

    }

    @Override
    public void visit(NumericBind bind) {

    }

    @Override
    public void visit(KeepExpression aexpr) {

    }

    @Override
    public void visit(MySQLGroupConcat groupConcat) {

    }

    @Override
    public void visit(ExpressionList<?> expressionList) {

    }

    @Override
    public void visit(RowConstructor rowConstructor) {

    }

    @Override
    public void visit(RowGetExpression rowGetExpression) {

    }

    @Override
    public void visit(OracleHint hint) {

    }

    @Override
    public void visit(TimeKeyExpression timeKeyExpression) {

    }

    @Override
    public void visit(DateTimeLiteralExpression literal) {

    }

    @Override
    public void visit(NotExpression aThis) {

    }

    @Override
    public void visit(NextValExpression aThis) {

    }

    @Override
    public void visit(CollateExpression aThis) {

    }

    @Override
    public void visit(SimilarToExpression aThis) {

    }

    @Override
    public void visit(ArrayExpression aThis) {

    }

    @Override
    public void visit(ArrayConstructor arrayConstructor) {

    }

    @Override
    public void visit(VariableAssignment variableAssignment) {

    }

    @Override
    public void visit(XMLSerializeExpr xmlSerializeExpr) {

    }

    @Override
    public void visit(TimezoneExpression timezoneExpression) {

    }

    @Override
    public void visit(JsonAggregateFunction jsonAggregateFunction) {

    }

    @Override
    public void visit(JsonFunction jsonFunction) {

    }

    @Override
    public void visit(ConnectByRootOperator connectByRootOperator) {

    }

    @Override
    public void visit(OracleNamedFunctionParameter oracleNamedFunctionParameter) {

    }

    /**
     * select *
     * 当没有别名直接* 的时候，找同层的表的全部字段，作为查询的全部字段
     *
     * @author liutangqi
     * @date 2024/3/5 11:01
     * @Param [allColumns]
     **/
    @Override
    public void visit(AllColumns allColumns) {
        //本层的全部字段
        Map<String, Set<FieldInfoDto>> fieldMap = Optional.ofNullable(this.getLayerFieldTableMap().get(String.valueOf(this.getLayer()))).orElse(new FieldHashMapWrapper<>());

        //将本层全部字段放到 select的map中
        for (Map.Entry<String, Set<FieldInfoDto>> fieldInfoEntry : fieldMap.entrySet()) {
            JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), fieldInfoEntry.getKey(), fieldInfoEntry.getValue());
        }

    }

    /**
     * select 别名.*
     * 从本层中找到别名的这张表的全部字段
     *
     * @author liutangqi
     * @date 2024/3/5 11:01
     * @Param [allTableColumns]
     **/
    @Override
    public void visit(AllTableColumns allTableColumns) {
        //获取本层涉及到的表的全部字段
        Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap = this.getLayerFieldTableMap();
        Map<String, Set<FieldInfoDto>> fieldTableMap = Optional.ofNullable(layerFieldTableMap.get(String.valueOf(this.getLayer()))).orElse(new FieldHashMapWrapper<>());

        //获取其中叫这个别名的全部字段
        String tableName = allTableColumns.getTable().getName();
        Map<String, Set<FieldInfoDto>> fieldMap = fieldTableMap
                .entrySet()
                .stream()
                .filter(f -> StringUtils.fieldEquals(f.getKey(), tableName))
                .collect(FieldHashMapWrapper::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), (map1, map2) -> map1.putAll(map2));

        //将本层全部字段放到 select的map中
        for (Map.Entry<String, Set<FieldInfoDto>> fieldInfoEntry : fieldMap.entrySet()) {
            JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), fieldInfoEntry.getKey(), fieldInfoEntry.getValue());
        }
    }

    @Override
    public void visit(AllValue allValue) {

    }

    @Override
    public void visit(IsDistinctExpression isDistinctExpression) {

    }

    @Override
    public void visit(GeometryDistance geometryDistance) {

    }

    /**
     * select
     * (select 字段 from xxx where)
     * from
     * 这种语法
     * 这种语法内层肯定只能有一个值，这个值的别名有点特殊，不是来自内层select的别名，那个是无意义的，真正的别名是整个内层select的别名
     * 备注：内层的select 查询的字段现在已经兼容可以查询内层关联的表字段，但是这种写法不建议，甚至有点呆
     *
     * @author liutangqi
     * @date 2025/3/10 9:58
     * @Param [select]
     **/
    @Override
    public void visit(Select subSelect) {
        //1.采用独立存储空间单独解析当前子查询的语法
        FieldParseParseTableSelectVisitor sFieldSelectItemVisitor = FieldParseParseTableSelectVisitor.newInstanceIndividualMap(this);
        subSelect.getPlainSelect().accept(sFieldSelectItemVisitor);

        //2.找出上面新解析出的结果，只取这一层的，其它层的结果不需要关心（因为这个结果需要单独处理别名 这种语法下select （select ） 内层select的别名是没有意义的，是以外层的select语句为准的,select 的内层关联的表字段也是没有意义的）
        Map<String, Set<FieldInfoDto>> newSelectTableFieldMap = JsqlparserUtil.parseNewlyIncreased(this.getLayerSelectTableFieldMap().getOrDefault(String.valueOf(this.getLayer()), new HashMap<>()), sFieldSelectItemVisitor.getLayerSelectTableFieldMap().getOrDefault(String.valueOf(this.getLayer()), new HashMap<>()));

        //3.结果合并 (注意：新增加的结果的别名需要修改成外层select的别名)
        for (Map.Entry<String, Set<FieldInfoDto>> fieldInfoEntry : newSelectTableFieldMap.entrySet()) {
            //4.1将这个字段的别名重新设置（这种语法下select （select ） 内层select的别名是没有意义的，是以外层的select语句为准的）
            Set<FieldInfoDto> fieldInfoDtos = fieldInfoEntry.getValue().stream().map(m -> FieldInfoDto.builder().sourceTableName(m.getSourceTableName()).sourceColumn(m.getSourceColumn()).fromSourceTable(m.isFromSourceTable()).columnName(Optional.ofNullable(this.alias).map(Alias::getName).orElse(subSelect.toString())).build()).collect(Collectors.toSet());
            //4.2 结果合并
            JsqlparserUtil.putFieldInfo(this.getLayerSelectTableFieldMap(), this.getLayer(), fieldInfoEntry.getKey(), fieldInfoDtos);
        }
    }

    @Override
    public void visit(TranscodingFunction transcodingFunction) {

    }

    @Override
    public void visit(TrimFunction trimFunction) {

    }

    @Override
    public void visit(RangeExpression rangeExpression) {

    }

    @Override
    public void visit(TSQLLeftJoin tsqlLeftJoin) {

    }

    @Override
    public void visit(TSQLRightJoin tsqlRightJoin) {

    }
}
