package com.sangsang.util.visitor;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.dbencrtptor.DBDecryptExpressionVisitor;
import com.sangsang.visitor.dbencrtptor.DBDecryptFromItemVisitor;
import com.sangsang.visitor.dbencrtptor.DBDecryptSelectVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.OutputClause;
import net.sf.jsqlparser.statement.ReturningClause;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author liutangqi
 * @date 2026/11/3 15:18
 */
public class EncryptorDbVisitorUtil {

    /**
     * 对DML RETURNING返回列进行解密处理
     *
     * @param returningClause     DML语句中的RETURNING语法对象
     * @param table               DML语句操作的目标表
     * @param baseFieldParseTable DML目标表对应的字段解析结果
     * @author liutangqi & gpt-5.6-sol
     * @date 2026/11/12 19:15
     **/
    public static void returning(ReturningClause returningClause,
                                 Table table,
                                 BaseFieldParseTable baseFieldParseTable) {
        //1.没有返回列时不处理
        if (returningClause == null) {
            return;
        }
        //2.根据是否存在INTO数据项，区分普通结果集和变量赋值语法，into的语法这里是不能出现别名的
        boolean resultSet = CollectionUtils.isEmpty(returningClause.getDataItems());
        //3.解析并改写RETURNING字段
        List<SelectItem<?>> selectItems = EncryptorDbVisitorUtil.resultItems(returningClause, table, baseFieldParseTable, resultSet);
        returningClause.clear();
        returningClause.addAll(selectItems);
    }

    /**
     * 对DML OUTPUT返回列进行解密处理
     *
     * @param outputClause        DML语句中的OUTPUT语法对象
     * @param table               DML语句操作的目标表
     * @param baseFieldParseTable DML目标表对应的字段解析结果
     * @author liutangqi & gpt-5.6-sol
     * @date 2026/11/12 19:15
     **/
    public static void output(OutputClause outputClause,
                              Table table,
                              BaseFieldParseTable baseFieldParseTable) {
        //1.没有OUTPUT返回列时不处理
        if (outputClause == null) {
            return;
        }

        //2.OUTPUT INTO具体表时，INTO目标列不能使用函数，只能将目标列作为上游字段处理OUTPUT表达式
        if (outputClause.getOutputTable() != null) {
            EncryptorDbVisitorUtil.outputInto(outputClause, table, baseFieldParseTable);
            return;
        }

        //3.OUTPUT INTO表变量时无法获取真实表结构，不进行处理
        if (outputClause.getTableVariable() != null) {
            return;
        }

        //4.解析并改写普通OUTPUT结果列
        outputClause.setSelectItemList(EncryptorDbVisitorUtil.resultItems(outputClause.getSelectItemList(), table, baseFieldParseTable, true));
    }

    /**
     * 对DML OUTPUT INTO具体表的OUTPUT表达式进行加解密处理
     * 注意：INTO后的目标列不允许使用函数，所以将目标列作为上游字段，按位置处理对应的OUTPUT表达式
     *
     * @param outputClause        DML语句中的OUTPUT INTO语法对象
     * @param table               DML语句操作的目标表
     * @param baseFieldParseTable DML目标表对应的字段解析结果
     * @author liutangqi & gpt-5.6-sol
     * @date 2026/11/17 15:20
     **/
    private static void outputInto(OutputClause outputClause,
                                   Table table,
                                   BaseFieldParseTable baseFieldParseTable) {
        //1.展开OUTPUT表达式中的*，得到实际写入INTO目标表的字段列表
        List<SelectItem<?>> outputItems = JsqlparserUtil.perfectDmlResultItems(outputClause.getSelectItemList(), table);
        List<String> outputColumns = outputClause.getColumnList();

        //2.OUTPUT表达式和INTO目标列必须按位置一一对应，无法对应时不进行改写
        if (CollectionUtils.isEmpty(outputItems) || CollectionUtils.isEmpty(outputColumns) || outputItems.size() != outputColumns.size()) {
            return;
        }

        //3.直接从表结构缓存中获取INTO目标列对应的上游加密配置，避免将INTO目标表混入当前DML字段作用域
        Map<String, FieldEncryptor> outputFieldEncryptMap = TableCache.getTableFieldEncryptInfo()
                .getOrDefault(outputClause.getOutputTable().getName(), new FieldHashMapWrapper<>());
        List<FieldEncryptor> upstreamFieldEncryptors = outputColumns.stream()
                .map(outputFieldEncryptMap::get)
                .collect(Collectors.toList());

        //4.根据INTO目标列是否密文存储，逐项处理对应的OUTPUT表达式
        for (int i = 0; i < outputItems.size(); i++) {
            SelectItem outputItem = outputItems.get(i);
            Expression expression = outputItem.getExpression();
            DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(baseFieldParseTable,
                    EncryptorFunctionEnum.UPSTREAM_COLUMN,
                    upstreamFieldEncryptors.get(i),
                    table);
            expression.accept(expressionVisitor);
            outputItem.setExpression(Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(expression));
        }
        outputClause.setSelectItemList(outputItems);
    }

    /**
     * 对DML结果字段进行解密处理
     *
     * @param selectItems         DML语句中的返回字段列表
     * @param table               当前DML语句主表（PS：如果DML返回的是*的话，有点特殊，这里的*是只包含这个主表的字段）
     * @param baseFieldParseTable DML目标表对应的字段解析结果
     * @param resultSet           是否形成普通结果集，形成结果集时需要保留处理前的字段别名
     * @author liutangqi & gpt-5.6-sol
     * @date 2026/11/12 19:15
     **/
    private static List<SelectItem<?>> resultItems(List<SelectItem<?>> selectItems,
                                                   Table table,
                                                   BaseFieldParseTable baseFieldParseTable,
                                                   boolean resultSet) {
        //1.没有返回列时直接返回原结果
        if (CollectionUtils.isEmpty(selectItems)) {
            return selectItems;
        }

        //2.展开*并保留当前DML返回列的字段顺序
        List<SelectItem<?>> resultItems = JsqlparserUtil.perfectDmlResultItems(selectItems, table);

        //3.逐列生成数据库解密表达式，注意：这里语法特殊，SqlServer里面有专门的关键字 inserted deleted ，其实指向的是table主表的数据，所以这里去匹配关系的时候，所属的表使用table
        for (SelectItem selectItem : resultItems) {
            Expression expression = selectItem.getExpression();
            DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(baseFieldParseTable,
                    EncryptorFunctionEnum.DEFAULT_DECRYPTION,
                    null,
                    table);
            expression.accept(expressionVisitor);
            //如果形成普通结果集，形成结果集时需要保留处理前的字段别名
            if (resultSet) {
                selectItem.setAlias(Optional.ofNullable(selectItem.getAlias()).orElse(expressionVisitor.getAlias()));
            }
            selectItem.setExpression(Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(expression));
        }
        //4.返回改写后的字段列表
        return resultItems;
    }


    /**
     * 对CTE语法进行加解密处理
     * 栗如：with x as (select phone from tb_user) select phone from x
     *
     * @author liutangqi
     * @date 2026/11/4 10:00
     * @Param [withItems]
     **/
    public static void cte(List<WithItem> withItems) {
        if (CollectionUtils.isEmpty(withItems)) {
            return;
        }
        for (WithItem withItem : withItems) {
            Select select = withItem.getSelect();
            if (select == null) {
                continue;
            }
            //CTE内部是一个独立的查询作用域，单独解析字段信息并进行加解密处理
            FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
            select.accept(fieldParseTableSelectVisitor);
            select.accept(DBDecryptSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor));
        }
    }

    /**
     * 对expressionList表达式进行解密
     *
     * @author liutangqi
     * @date 2026/11/3 15:18
     * @Param [baseFieldParseTable, upstreamNeedEncryptFieldEncryptor, expressionList]
     **/
    public static void expressionList(BaseFieldParseTable baseFieldParseTable, List<FieldEncryptor> upstreamNeedEncryptFieldEncryptor, ExpressionList<Expression> expressionList) {
        if (CollectionUtils.isEmpty(upstreamNeedEncryptFieldEncryptor) || CollectionUtils.isEmpty(expressionList) || upstreamNeedEncryptFieldEncryptor.size() != expressionList.size()) {
            return;
        }
        for (int i = 0; i < expressionList.size(); i++) {
            Expression exp = expressionList.get(i);
            FieldEncryptor upstreamFieldEncryptor = upstreamNeedEncryptFieldEncryptor.get(i);
            DBDecryptExpressionVisitor dbDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(baseFieldParseTable, EncryptorFunctionEnum.UPSTREAM_COLUMN, upstreamFieldEncryptor);
            exp.accept(dbDecryptExpressionVisitor);
            expressionList.set(i, Optional.ofNullable(dbDecryptExpressionVisitor.getProcessedExpression()).orElse(exp));
        }
    }


    /**
     * 对updateSets进行解密
     *
     * @author liutangqi
     * @date 2026/11/3 15:46
     * @Param [updateSets, baseFieldParseTable]
     **/
    public static void updateSets(List<UpdateSet> updateSets, BaseFieldParseTable baseFieldParseTable) {
        if (CollectionUtils.isEmpty(updateSets)) {
            return;
        }
        for (UpdateSet updateSet : updateSets) {
            List<Column> columns = updateSet.getColumns();
            ExpressionList<Expression> expressions = (ExpressionList<Expression>) updateSet.getValues();
            //处理每对需要加密的字段
            for (int i = 0; i < columns.size(); i++) {
                Column column = columns.get(i);
                Expression expression = expressions.get(i);
                //左边是否需要加解密
                FieldEncryptor leftFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(column, baseFieldParseTable);
                //根据左边是否密文存储来对右边进行处理
                DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(baseFieldParseTable, EncryptorFunctionEnum.UPSTREAM_COLUMN, leftFieldEncryptor);
                expression.accept(sDecryptExpressionVisitor);
                expressions.set(i, Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(expression));
            }
        }
    }

    /**
     * 不考虑上游对应字段，对Expression进行解密处理
     *
     * @author liutangqi
     * @date 2026/11/4 16:24
     * @Param [expression, baseFieldParseTable]
     **/
    public static Expression expressionDecryption(Expression expression, BaseFieldParseTable baseFieldParseTable) {
        if (expression == null) {
            return null;
        }
        DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(baseFieldParseTable, EncryptorFunctionEnum.DEFAULT_DECRYPTION, null);
        expression.accept(expressionVisitor);
        return Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(expression);
    }

    /**
     * 对join的表和on条件进行处理
     *
     * @author liutangqi
     * @date 2026/11/11 17:30
     * @Param [joins, baseFieldParseTable]
     **/
    public static void joins(List<Join> joins, BaseFieldParseTable baseFieldParseTable) {
        if (CollectionUtils.isEmpty(joins)) {
            return;
        }
        DBDecryptFromItemVisitor dbDecryptFromItemVisitor = DBDecryptFromItemVisitor.newInstanceCurLayer(baseFieldParseTable);
        for (Join join : joins) {
            //1.处理join的表
            FromItem rightItem = join.getRightItem();
            if (rightItem != null) {
                rightItem.accept(dbDecryptFromItemVisitor);
            }

            //2.处理join的on条件
            if (CollectionUtils.isEmpty(join.getOnExpressions())) {
                continue;
            }
            List<Expression> decryptExpressions = new ArrayList<>();
            for (Expression expression : join.getOnExpressions()) {
                decryptExpressions.add(EncryptorDbVisitorUtil.expressionDecryption(expression, baseFieldParseTable));
            }
            join.setOnExpressions(decryptExpressions);
        }
    }
}
