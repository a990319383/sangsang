package com.sangsang.util.visitor;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.dbencrtptor.DBDecryptExpressionVisitor;
import com.sangsang.visitor.dbencrtptor.DBDecryptSelectVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.List;
import java.util.Optional;

/**
 * @author liutangqi
 * @date 2026/11/3 15:18
 */
public class EncryptorDbVisitorUtil {
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
    public static void expressionList(BaseFieldParseTable baseFieldParseTable,
                                      List<FieldEncryptor> upstreamNeedEncryptFieldEncryptor,
                                      ExpressionList<Expression> expressionList) {
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
    public static void updateSets(List<UpdateSet> updateSets,
                                  BaseFieldParseTable baseFieldParseTable) {
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
    public static Expression expressionDecryption(Expression expression,
                                                  BaseFieldParseTable baseFieldParseTable) {
        if (expression == null) {
            return null;
        }
        DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(baseFieldParseTable,
                EncryptorFunctionEnum.DEFAULT_DECRYPTION,
                null);
        expression.accept(expressionVisitor);
        return Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(expression);
    }
}
