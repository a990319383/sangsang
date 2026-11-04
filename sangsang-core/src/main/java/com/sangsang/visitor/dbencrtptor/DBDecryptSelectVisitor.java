package com.sangsang.visitor.dbencrtptor;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.visitor.EncryptorDbVisitorUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * select 语句  字段解密 入口
 * 备注：包含 select 和 where 语句一同加解密
 *
 * @author liutangqi
 * @date 2024/2/29 15:43
 */
public class DBDecryptSelectVisitor extends BaseFieldParseTable implements SelectVisitor {

    /**
     * 当涉及到上游不同字段需要进行不同的加解密场景时。这个字段传上游的需要加密的项的索引下标的@FieldEncryptor
     * 例如：
     * 场景1：insert(a,b,c) select( x,y,z) 当 a,c需要密文存储，b 不需要时，这值就是[@FieldEncryptor,null,@FieldEncryptor]
     * 场景2：xxx in(select x,y,z) ，当xxx 需要密文存储时，这个值就是[@FieldEncryptor]
     * 不涉及到这个项在上游有对应的列时，这个字段为null
     */
    private List<FieldEncryptor> upstreamNeedEncryptFieldEncryptor;


    /**
     * 获取当前层解析实例
     *
     * @author liutangqi
     * @date 2025/3/2 22:22
     * @Param [baseFieldParseTable, encryptorFunction]
     **/
    public static DBDecryptSelectVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable,
                                                             List<FieldEncryptor> upstreamNeedEncryptFieldEncryptor) {
        return new DBDecryptSelectVisitor(upstreamNeedEncryptFieldEncryptor,
                baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap());

    }

    /**
     * 获取当前层解析实例
     *
     * @author liutangqi
     * @date 2025/3/2 22:22
     * @Param [baseFieldParseTable, encryptorFunction]
     **/
    public static DBDecryptSelectVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new DBDecryptSelectVisitor(null,
                baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap());

    }

    /**
     * 获取下一层解析实例
     *
     * @author liutangqi
     * @date 2025/3/2 22:22
     * @Param [baseFieldParseTable, encryptorFunction]
     **/
    public static DBDecryptSelectVisitor newInstanceNextLayer(BaseFieldParseTable baseFieldParseTable,
                                                              List<FieldEncryptor> upstreamNeedEncryptFieldEncryptor) {
        return new DBDecryptSelectVisitor(upstreamNeedEncryptFieldEncryptor,
                (baseFieldParseTable.getLayer() + 1),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap());
    }

    /**
     * 获取下一层解析实例
     *
     * @author liutangqi
     * @date 2025/3/2 22:22
     * @Param [baseFieldParseTable, encryptorFunction]
     **/
    public static DBDecryptSelectVisitor newInstanceNextLayer(BaseFieldParseTable baseFieldParseTable) {
        return new DBDecryptSelectVisitor(null,
                (baseFieldParseTable.getLayer() + 1),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap());

    }


    private DBDecryptSelectVisitor(List<FieldEncryptor> upstreamNeedEncryptFieldEncryptor,
                                   int layer,
                                   Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                                   Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
        this.upstreamNeedEncryptFieldEncryptor = Optional.ofNullable(upstreamNeedEncryptFieldEncryptor).orElse(new ArrayList<>());
    }

    /**
     * 场景1：
     * select
     * (select xx from tb)as xxx -- 这种语法
     * from tb
     * 场景2：
     * xxx in (select xxx from) -- 括号里面的这种语法
     *
     * @author liutangqi
     * @date 2025/3/12 13:58
     * @Param [subSelect]
     **/
    @Override
    public void visit(ParenthesedSelect subSelect) {
        //解密子查询内容（注意：这里层数是当前层，这个的解析结果需要和外层在同一层级）
        Optional.ofNullable(subSelect.getSelect())
                .ifPresent(p -> p.accept(DBDecryptSelectVisitor.newInstanceCurLayer(this, this.upstreamNeedEncryptFieldEncryptor)));
    }

    /**
     * 普通的select 查询
     *
     * @author liutangqi
     * @date 2024/2/29 16:12
     * @Param [plainSelect]
     **/
    @Override
    public void visit(PlainSelect plainSelect) {
        //1.处理CTE内部的查询
        EncryptorDbVisitorUtil.cte(plainSelect.getWithItemsList());

        //2.解密 from 的表 （解密所有内层的语句）
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem != null) {
            DBDecryptFromItemVisitor sDecryptFromItemVisitor = DBDecryptFromItemVisitor.newInstanceCurLayer(this, this.upstreamNeedEncryptFieldEncryptor);
            fromItem.accept(sDecryptFromItemVisitor);
        }

        //3.将 select *  select 别名.* 转换为select 字段
        List<SelectItem> selectItems = plainSelect.getSelectItems().stream()
                .map(m -> JsqlparserUtil.perfectAllColumns(m, this.getLayer(), this.getLayerFieldTableMap(), this.upstreamNeedEncryptFieldEncryptor))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        //4.将其中的 select 的每一项 如果需要解密的进行解密处理 （不需要处理的 * ，别名.* 原样返回）
        List<SelectItem<?>> itemRes = new ArrayList<>();
        for (int i = 0; i < selectItems.size(); i++) {
            SelectItem sItem = selectItems.get(i);
            Expression expression = sItem.getExpression();
            //根据当前项对应的上游字段是否密文存储来决定下游使用加密还是使用解密
            FieldEncryptor upstreamFieldEncryptor = this.upstreamNeedEncryptFieldEncryptor.size() > i ? this.upstreamNeedEncryptFieldEncryptor.get(i) : null;
            DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this, EncryptorFunctionEnum.UPSTREAM_COLUMN, upstreamFieldEncryptor);
            expression.accept(sDecryptExpressionVisitor);
            //之前有别名就用之前的，之前没有的话，采用处理后的别名
            sItem.setAlias(Optional.ofNullable(sItem.getAlias()).orElse(sDecryptExpressionVisitor.getAlias()));
            sItem.setExpression(Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(expression));
            itemRes.add(sItem);
        }

        //5.修改原sql查询项
        plainSelect.setSelectItems(itemRes);

        //6.对where条件后的进行解密
        if (plainSelect.getWhere() != null) {
            Expression where = plainSelect.getWhere();
            DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this, EncryptorFunctionEnum.DEFAULT_DECRYPTION, null);
            where.accept(sDecryptExpressionVisitor);
            //处理后的条件赋值
            plainSelect.setWhere(Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(where));
        }

        //7.处理join
        List<Join> joins = plainSelect.getJoins();
        if (CollectionUtils.isNotEmpty(joins)) {
            DBDecryptFromItemVisitor sDecryptFromItemVisitor = DBDecryptFromItemVisitor.newInstanceCurLayer(this);
            for (Join join : joins) {
                //7.1 处理join的表
                FromItem joinRightItem = join.getRightItem();
                joinRightItem.accept(sDecryptFromItemVisitor);
                //7.2 处理 on
                List<Expression> dencryptExpressions = new ArrayList<>();
                for (Expression expression : join.getOnExpressions()) {
                    DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this, EncryptorFunctionEnum.DEFAULT_DECRYPTION, null);
                    expression.accept(sDecryptExpressionVisitor);
                    dencryptExpressions.add(Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(expression));
                }
                //处理后的结果赋值
                join.setOnExpressions(dencryptExpressions);
            }
        }
    }

    /**
     * union 查询
     *
     * @author liutangqi
     * @date 2024/2/29 16:12
     * @Param [setOpList]
     **/
    @Override
    public void visit(SetOperationList setOpList) {
        //处理挂在union语句上的CTE定义
        EncryptorDbVisitorUtil.cte(setOpList.getWithItemsList());

        List<Select> selects = setOpList.getSelects();

        List<Select> resSelectBody = new ArrayList<>();
        for (int i = 0; i < selects.size(); i++) {
            Select select = selects.get(i);
            //解析每个union的语句自己拥有的字段信息
            FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
            select.accept(fieldParseTableSelectVisitor);

            //需要加密的字段进行加密处理
            DBDecryptSelectVisitor sDecryptSelectVisitor = DBDecryptSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor, this.upstreamNeedEncryptFieldEncryptor);
            select.accept(sDecryptSelectVisitor);

            //维护加解密之后的语句
            resSelectBody.add(select);
        }
        setOpList.setSelects(resSelectBody);
    }

    @Override
    public void visit(WithItem withItem) {
        EncryptorDbVisitorUtil.cte(Arrays.asList(withItem));
    }

    /**
     * insert 语句 values后面的处理逻辑
     * SQL 形态              根节点                       子节点
     * ━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * VALUES (?)            ExpressionList               Parenthesis(?)
     * ────────────────────  ───────────────────────────  ────────────────────────────────
     * VALUES (?),(?)        ExpressionList               多个 Parenthesis(?)
     * ────────────────────  ───────────────────────────  ────────────────────────────────
     * VALUES (?,?)          ParenthesedExpressionList    多个实际字段值
     * ────────────────────  ───────────────────────────  ────────────────────────────────
     * VALUES (?,?),(?,?)    ExpressionList               多个 ParenthesedExpressionList
     *
     * @author liutangqi
     * @date 2025/3/12 17:45
     * @Param [aThis]
     **/
    @Override
    public void visit(Values aThis) {
        //1.外层是 ParenthesedExpressionList (对应形态3) 用例对应i6
        if (aThis.getExpressions() instanceof ParenthesedExpressionList) {
            ParenthesedExpressionList parenthesedExpressionList = (ParenthesedExpressionList) aThis.getExpressions();
            EncryptorDbVisitorUtil.expressionList(this, this.upstreamNeedEncryptFieldEncryptor, parenthesedExpressionList);
        }

        //2.外层是 ExpressionList (对应形态1、2、4)
        if (aThis.getExpressions() instanceof ExpressionList) {
            ExpressionList expressionList = aThis.getExpressions();
            //2.1 形态1、对应测试用例 i13
            if (expressionList.size() == 1 && expressionList.getExpressions().get(0) instanceof Parenthesis) {
                Parenthesis parenthesis = (Parenthesis) expressionList.get(0);
                ParenthesedExpressionList<Expression> parenthesedExpressionList = new ParenthesedExpressionList<>();
                parenthesedExpressionList.add(parenthesis.getExpression());
                EncryptorDbVisitorUtil.expressionList(this, this.upstreamNeedEncryptFieldEncryptor, parenthesedExpressionList);
                aThis.setExpressions(parenthesedExpressionList);
            }
            //2.2 形态2、对应测试用例 i14
            else if (expressionList.size() > 1 && expressionList.getExpressions().get(0) instanceof Parenthesis) {
                for (int i = 0; i < expressionList.size(); i++) {
                    Parenthesis parenthesis = (Parenthesis) expressionList.get(i);
                    ParenthesedExpressionList<Expression> parenthesedExpressionList = new ParenthesedExpressionList<>();
                    parenthesedExpressionList.add(parenthesis.getExpression());
                    EncryptorDbVisitorUtil.expressionList(this, this.upstreamNeedEncryptFieldEncryptor, parenthesedExpressionList);
                    expressionList.set(i, parenthesedExpressionList);
                }
            }
            //2.3 形态4，对应测试用例 i1
            else if (expressionList.size() > 1 && expressionList.getExpressions().get(0) instanceof ParenthesedExpressionList) {
                for (Object exp : expressionList) {
                    ParenthesedExpressionList parenthesedExpressionList = (ParenthesedExpressionList) exp;
                    EncryptorDbVisitorUtil.expressionList(this, this.upstreamNeedEncryptFieldEncryptor, parenthesedExpressionList);
                }
            }
        }
    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(TableStatement tableStatement) {

    }

}
