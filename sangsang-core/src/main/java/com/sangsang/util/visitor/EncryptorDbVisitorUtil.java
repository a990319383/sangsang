package com.sangsang.util.visitor;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.dbencrtptor.DBDecryptExpressionVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.List;
import java.util.Optional;

/**
 * @author liutangqi
 * @date 2026/11/3 15:18
 */
public class EncryptorDbVisitorUtil {
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

}
