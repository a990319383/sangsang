package com.sangsang.util.visitor;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.util.CollectionUtils;
import com.sangsang.visitor.dbencrtptor.DBDecryptExpressionVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

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
}
