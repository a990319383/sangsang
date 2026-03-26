package com.sangsang.visitor.dbencrtptor;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.dto.BaseDEcryptParseTable;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import lombok.Getter;
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
 * 将每一项字段  如果需要加密的，则进行加密
 * 备注：目前主要对Column 类型进行了处理
 *
 * @author liutangqi
 * @date 2024/2/29 16:50
 */
public class DBDecryptExpressionVisitor extends BaseDEcryptParseTable implements ExpressionVisitor {
    /**
     * 列经过处理后的别名
     * 当字段经过加解密函数处理后，这个值就会被赋值
     * 注意:这里的getter方法获取到的别名，如果别名不需要额外处理，返回的是原有的别名
     */
    @Getter
    private Alias alias;

    /**
     * 经过加解密处理后的表达式，如果没有处理过，则这个字段为null
     * 注意：这里getter方法不能像Transformation一样获取后立马清除，达到visitor复用的效果，因为visitor里面除了处理后的表达式还有其他属性，如果处理后删除的话，会导致其它属性错乱
     * 这里也不能立即把其它属性给清除了，因为其它的属性在下游的其它地方可能会有使用
     **/
    @Getter
    private Expression processedExpression;

    /**
     * 获取当前层的解析对象
     * （一般是上层节点调用）
     *
     * @author liutangqi
     * @date 2025/2/28 23:09
     * @Param [baseFieldParseTable, alias, expression]
     **/
    public static DBDecryptExpressionVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable,
                                                                 EncryptorFunctionEnum encryptorFunctionEnum,
                                                                 FieldEncryptor upstreamFieldEncryptor) {
        return new DBDecryptExpressionVisitor(baseFieldParseTable.getLayer(),
                encryptorFunctionEnum,
                upstreamFieldEncryptor,
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap());
    }

    /**
     * 获取当前层的解析对象
     * （一般是当前节点调用，当前节点调用请不用直接accept(this),每个节点处理完毕的alias 和expression 需要单独保存，复用会导致错乱）
     * 注意：当前节点调用的时候，可能上游对应字段的注解不为空，但是再次创建实例时，这里将上游字段注解进行了清空
     *
     * @author liutangqi
     * @date 2025/2/28 23:09
     * @Param [baseFieldParseTable, expression]
     **/
    public static DBDecryptExpressionVisitor newInstanceCurLayer(BaseDEcryptParseTable baseDEcryptParseTable) {
        return new DBDecryptExpressionVisitor(
                baseDEcryptParseTable.getLayer(),
                baseDEcryptParseTable.getEncryptorFunctionEnum(),
                //注意：baseDEcryptParseTable的上游对应字段，到这里了就不是当前表达式的上游对应字段了，所以这里进行了清空
                null,
                baseDEcryptParseTable.getLayerSelectTableFieldMap(),
                baseDEcryptParseTable.getLayerFieldTableMap());
    }

    private DBDecryptExpressionVisitor(int layer,
                                       EncryptorFunctionEnum encryptorFunctionEnum,
                                       FieldEncryptor upstreamFieldEncryptor,
                                       Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                                       Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, encryptorFunctionEnum, upstreamFieldEncryptor, layerSelectTableFieldMap, layerFieldTableMap);
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

    /**
     * 对于function的进行加密
     *
     * @author liutangqi
     * @date 2024/3/15 10:05
     * @Param [function]
     **/
    @Override
    public void visit(Function function) {
        List<Expression> expressions = Optional.ofNullable((ExpressionList<Expression>) function.getParameters())
                .orElse(new ExpressionList<>())
                .stream()
                .map(m -> {
                    DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
                    m.accept(sDecryptExpressionVisitor);
                    return Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(m);
                }).collect(Collectors.toList());
        if (function.getParameters() != null) {
            function.setParameters(new ExpressionList(expressions));
        }

    }

    @Override
    public void visit(SignedExpression signedExpression) {

    }

    /**
     * update tb set xxx = ? 这种语法的?
     *
     * @author liutangqi
     * @date 2025/3/13 11:08
     * @Param [jdbcParameter]
     **/
    @Override
    public void visit(JdbcParameter jdbcParameter) {
        //注意：这种是常量，肯定不是密文存储，头上肯定没注解，所以下面 currentFieldEncryptor 是null
        Expression disposeExp = this.getEncryptorFunctionEnum()
                .getFun()
                .dispose(this.getUpstreamFieldEncryptor(), null, jdbcParameter);
        //处理结果赋值
        this.processedExpression = disposeExp;
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

    /**
     * 括号括起来的一堆条件
     *
     * @author liutangqi
     * @date 2025/3/1 13:41
     * @Param [parenthesis]
     **/
    @Override
    public void visit(Parenthesis parenthesis) {
        //解析括号括起来的表达式
        Expression exp = parenthesis.getExpression();
        DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
        exp.accept(sDecryptExpressionVisitor);
        parenthesis.setExpression(Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(exp));
    }

    @Override
    public void visit(StringValue stringValue) {

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
        //db模式处理左右表达式
        JsqlparserUtil.visitDbBinaryExpression(this, andExpression);
    }

    @Override
    public void visit(OrExpression orExpression) {
        //db模式处理左右表达式
        JsqlparserUtil.visitDbBinaryExpression(this, orExpression);
    }

    @Override
    public void visit(XorExpression xorExpression) {
        //db模式处理左右表达式
        JsqlparserUtil.visitDbBinaryExpression(this, xorExpression);
    }

    @Override
    public void visit(Between between) {

    }

    @Override
    public void visit(OverlapsCondition overlapsCondition) {

    }

    /**
     * select 语句中存在 case when 字段 = xxx then 这种语法的时候， 其中字段=xxx 会走这里的解析
     * where 语句中的 = 也会走这里解析
     *
     * @author liutangqi
     * @date 2024/7/30 16:49
     * @Param [equalsTo]
     **/
    @Override
    public void visit(EqualsTo equalsTo) {
        //db模式处理比较的表达式
        JsqlparserUtil.visitComparisonOperator(this, equalsTo);
    }

    /**
     * 大于的处理
     * 注意：大于，小于 也不能单独只加密常量的那部分，数值类型的字符串加密后是无法用于大于等于判断的，所以下面不能改成 JsqlparserUtil.visitComparisonOperator(this, greaterThan);
     *
     * @author liutangqi
     * @date 2025/7/3 9:22
     * @Param [greaterThan]
     **/
    @Override
    public void visit(GreaterThan greaterThan) {
        //db模式处理左右表达式
        JsqlparserUtil.visitDbBinaryExpression(this, greaterThan);
    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        //db模式处理左右表达式
        JsqlparserUtil.visitDbBinaryExpression(this, greaterThanEquals);
    }

    /**
     * in的处理比较复杂，大部分情况下，根据左边不同的类型处理右边的列，可以有效解决索引失效的问题
     * 语法1： xxx in (?,?)
     * 语法2： xxx in (select xxx from )
     * 语法3： ? in (select xxx from)
     * 语法4： (xxx,yyy) in ((?,?),(?,?))
     * 语法5： (xxx,yyy) in (select xxx,yyy from )
     * 语法6： concat("aaa",tu.phone) in (? , ?)
     * 语法7： (?,?) in (select xxx,yyy from )
     * 语法8： (xxx) in 左边是一个字段，但是使用括号包裹起来了，右边不管
     *
     * @author liutangqi
     * @date 2025/3/1 13:54
     * @Param [inExpression]
     **/
    @Override
    public void visit(InExpression inExpression) {
        //1.处理左边表达式（一般只需要记录左边需要密文存储的索引(1.1,1.2)，只有特殊情况下才需要对左边进行加解密处理(1.3)）
        Expression leftExpression = inExpression.getLeftExpression();
        //1.1 如果左边表达式是括号包裹起来的，将括号去了看里面的
        if (leftExpression instanceof Parenthesis) {
            leftExpression = ((Parenthesis) leftExpression).getExpression();
        }
        //1.2 记录左边字段需要密文存储的注解（注意：字段的顺序和下标一致，从0开始）
        List<FieldEncryptor> needEncryptFieldEncryptorList = new ArrayList<>();
        //1.3 左边是单列的常量或者是字段列时（对应语法1，语法2，语法3，语法8）
        if ((leftExpression instanceof Column) || (leftExpression instanceof JdbcParameter)) {
            //获取左边表达式是否是 Column 并且需要进行密文存储
            FieldEncryptor leftColumnFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(leftExpression, this);
            //这种情况左边只有一列，下标肯定只有一个，是0
            needEncryptFieldEncryptorList.add(NumberConstant.ZERO, leftColumnFieldEncryptor);
        }
        //1.4 左边是多值字段时（对应语法4，语法5，语法7）
        else if (leftExpression instanceof ParenthesedExpressionList) {
            ParenthesedExpressionList<Expression> leftExpressionList = (ParenthesedExpressionList) leftExpression;
            for (int i = 0; i < leftExpressionList.size(); i++) {
                //记录左边每个字段头上的@FieldEncryptor注解信息
                FieldEncryptor leftColumnFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(leftExpressionList.get(i), this);
                needEncryptFieldEncryptorList.add(i, leftColumnFieldEncryptor);
            }
        }
        //1.5 左边是其它情况时（对应语法6）
        else {
            //这种情况下，不维护左边的密文存储的下标，左右单独处理
            DBDecryptExpressionVisitor leftExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
            leftExpression.accept(leftExpressionVisitor);
            inExpression.setLeftExpression(Optional.ofNullable(leftExpressionVisitor.getProcessedExpression()).orElse(leftExpression));
        }

        //2.处理右边表达式
        Expression rightExpression = inExpression.getRightExpression();
        //2.1 当右边是多列，并且右边也是多列的集合时（对应语法4）
        if ((rightExpression instanceof ParenthesedExpressionList) && (((ParenthesedExpressionList) rightExpression).get(0)) instanceof ExpressionList) {
            ParenthesedExpressionList<ExpressionList> rightExpressionList = (ParenthesedExpressionList<ExpressionList>) rightExpression;
            for (ExpressionList expList : rightExpressionList) {
                for (int i = 0; i < expList.size(); i++) {
                    //根据左边是否明密文的情况处理右边
                    FieldEncryptor leftColumnFieldEncryptor = needEncryptFieldEncryptorList.get(i);
                    DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this, EncryptorFunctionEnum.UPSTREAM_COLUMN, leftColumnFieldEncryptor);
                    ((Expression) expList.get(i)).accept(sDecryptExpressionVisitor);
                    //处理结果赋值
                    expList.set(i, Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse((Expression) expList.get(i)));
                }
            }
        }
        //2.2 当右边是多列的其它情况（对应语法1,语法6）注意：此时左边肯定只有1列
        else if ((rightExpression instanceof ParenthesedExpressionList)) {
            ParenthesedExpressionList<Expression> rightExpressionList = (ParenthesedExpressionList<Expression>) rightExpression;
            //左边肯定只有一列，所以这里get(0)
            FieldEncryptor leftColumnFieldEncryptor = needEncryptFieldEncryptorList.size() > NumberConstant.ZERO ? needEncryptFieldEncryptorList.get(NumberConstant.ZERO) : null;
            for (int i = 0; i < rightExpressionList.size(); i++) {
                //根据左边是否明密文的情况处理右边
                DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this, EncryptorFunctionEnum.UPSTREAM_COLUMN, leftColumnFieldEncryptor);
                rightExpressionList.get(i).accept(sDecryptExpressionVisitor);
                //处理结果赋值
                rightExpressionList.set(i, Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(rightExpressionList.get(i)));
            }
        }
        //2.3 当右边是子查询时 （对应语法2，语法3，语法5，语法7）
        else if (rightExpression instanceof ParenthesedSelect) {
            ParenthesedSelect rightSelect = (ParenthesedSelect) rightExpression;
            //这种情况右边是一个完全独立的sql，单独解析 注意：子查询是有权限读取上游表字段信息的，所以这里单独解析的时候将上游的解析结果合并到下游
            FieldParseParseTableSelectVisitor fPTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceIndividualMap(this);
            rightSelect.accept(fPTableSelectVisitor);
            //对右边的sql进行加解密处理
            DBDecryptSelectVisitor dbDecryptSelectVisitor = DBDecryptSelectVisitor.newInstanceCurLayer(fPTableSelectVisitor, needEncryptFieldEncryptorList);
            rightSelect.accept(dbDecryptSelectVisitor);
        }
        //2.4 其它情况没单独解析右边
        else {
            DBDecryptExpressionVisitor rightExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
            rightExpression.accept(rightExpressionVisitor);
            inExpression.setRightExpression(Optional.ofNullable(rightExpressionVisitor.getProcessedExpression()).orElse(rightExpression));
        }
    }

    @Override
    public void visit(FullTextSearch fullTextSearch) {

    }

    @Override
    public void visit(IsNullExpression isNullExpression) {

    }

    @Override
    public void visit(IsBooleanExpression isBooleanExpression) {
        //解析表达式
        Expression leftExpression = isBooleanExpression.getLeftExpression();
        DBDecryptExpressionVisitor leftExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
        leftExpression.accept(leftExpressionVisitor);
        isBooleanExpression.setLeftExpression(Optional.ofNullable(leftExpressionVisitor.getProcessedExpression()).orElse(leftExpression));
    }

    /**
     * like的处理
     * 注意：这里只能分开处理左右表达式，不能单独只处理右边的常量，因为常量加密后like是无法进行匹配的
     *
     * @author liutangqi
     * @date 2025/7/3 9:20
     * @Param [likeExpression]
     **/
    @Override
    public void visit(LikeExpression likeExpression) {
        //db模式处理左右表达式
        JsqlparserUtil.visitDbBinaryExpression(this, likeExpression);
    }

    @Override
    public void visit(MinorThan minorThan) {
        //db模式处理左右表达式
        JsqlparserUtil.visitDbBinaryExpression(this, minorThan);
    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        //db模式处理左右表达式
        JsqlparserUtil.visitDbBinaryExpression(this, minorThanEquals);
    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        //db模式处理比较的表达式
        JsqlparserUtil.visitComparisonOperator(this, notEqualsTo);
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


    /**
     * select的每一个查询项
     *
     * @author liutangqi
     * @date 2024/2/29 17:39
     * @Param [column]
     **/
    @Override
    public void visit(Column column) {
        //1.判断当前列是否需要密文存储
        FieldEncryptor currentFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(column, this);

        //2.获取上游列是否需要密文存储
        FieldEncryptor upstreamFieldEncryptor = this.getUpstreamFieldEncryptor();

        //3.将此字段进行加解密处理
        Expression disposeExp = this.getEncryptorFunctionEnum().getFun().dispose(upstreamFieldEncryptor, currentFieldEncryptor, column);
        this.processedExpression = disposeExp;

        //4.别名处理（字段经过加密函数后，如果之前没有别名的话，需要用之前的字段名作为别名，不然ORM映射的时候会无法匹配
        if (!column.toString().equals(disposeExp.toString())) {
            this.alias = Optional.ofNullable(alias).orElse(new Alias(column.getColumnName()));
        }
    }

    /**
     * 场景1：
     * select
     * (select 字段 from xx )
     * from
     * 这种语法
     * 场景2：
     * xxx in (select xxx from tb)
     * 场景3：
     * exists (select xxx from tb)
     *
     * @author liutangqi
     * @date 2024/3/6 17:19
     * @Param [subSelect]
     **/
    @Override
    public void visit(Select subSelect) {
        //这种语法的里面都是单独的语句，所以这里将里层的语句单独解析一次
        //1.采用独立存储空间单独解析合并当前子查询的语法
        FieldParseParseTableSelectVisitor sFieldSelectItemVisitor = FieldParseParseTableSelectVisitor.newInstanceIndividualMap(this);
        subSelect.accept(sFieldSelectItemVisitor);

        //2.利用解析后的表结构Map进行子查询解密处理
        DBDecryptSelectVisitor sDecryptSelectVisitor = DBDecryptSelectVisitor.newInstanceCurLayer(sFieldSelectItemVisitor,
                Arrays.asList(this.getUpstreamFieldEncryptor()));
        subSelect.accept(sDecryptSelectVisitor);
    }

    /**
     * case 字段 when xxx then
     * case when 字段=xxx then
     *
     * @author liutangqi
     * @date 2024/7/30 15:35
     * @Param [caseExpression]
     **/
    @Override
    public void visit(CaseExpression caseExpression) {
        //处理case的条件
        Expression switchExpression = caseExpression.getSwitchExpression();
        if (switchExpression != null) {
            DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
            switchExpression.accept(expressionVisitor);
            caseExpression.setSwitchExpression(Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(switchExpression));
        }

        //处理when的条件
        if (!CollectionUtils.isEmpty(caseExpression.getWhenClauses())) {
            List<WhenClause> whenClauses = caseExpression.getWhenClauses().stream()
                    .map(m -> {
                        DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
                        m.accept(expressionVisitor);
                        // 这里返回的类型肯定是通过构造函数传输过去的，所以可以直接强转（这里过去是WhenClause WhenClause下一层才是Column才会转换类型）
                        return (WhenClause) (Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(m));
                    }).collect(Collectors.toList());
            caseExpression.setWhenClauses(whenClauses);
        }

        //处理else
        Expression elseExpression = caseExpression.getElseExpression();
        if (elseExpression != null) {
            DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
            elseExpression.accept(expressionVisitor);
            caseExpression.setElseExpression(Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(elseExpression));
        }
    }

    @Override
    public void visit(WhenClause whenClause) {
        Expression thenExpression = whenClause.getThenExpression();
        if (thenExpression != null) {
            DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
            thenExpression.accept(expressionVisitor);
            whenClause.setThenExpression(Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(thenExpression));
        }

        Expression whenExpression = whenClause.getWhenExpression();
        if (whenExpression != null) {
            DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
            whenExpression.accept(expressionVisitor);
            whenClause.setWhenExpression(Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(whenExpression));
        }
    }

    @Override
    public void visit(ExistsExpression existsExpression) {
        //解析表达式
        Expression rightExpression = existsExpression.getRightExpression();
        DBDecryptExpressionVisitor rightExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
        rightExpression.accept(rightExpressionVisitor);
        existsExpression.setRightExpression(Optional.ofNullable(rightExpressionVisitor.getProcessedExpression()).orElse(rightExpression));
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
        Expression leftExpression = cast.getLeftExpression();
        DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
        leftExpression.accept(expressionVisitor);
        cast.setLeftExpression(Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(leftExpression));
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
        //将每一项进行解密处理
        ExpressionList<?> expressionList = groupConcat.getExpressionList();
        List<Expression> newExpressions = new ArrayList<>();
        for (Expression exp : expressionList) {
            DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
            exp.accept(sDecryptExpressionVisitor);
            newExpressions.add(Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(exp));
        }

        //替换解密后的表达式
        groupConcat.setExpressionList(new ExpressionList(newExpressions));
    }

    @Override
    public void visit(ExpressionList expressionList) {
        for (int i = 0; i < expressionList.size(); i++) {
            Expression exp = (Expression) expressionList.get(i);
            DBDecryptExpressionVisitor dbDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
            exp.accept(dbDecryptExpressionVisitor);
            expressionList.set(i, Optional.ofNullable(dbDecryptExpressionVisitor.getProcessedExpression()).orElse(exp));
        }
    }

    /**
     * 多字段 in 的时候，左边的多字段会走这里
     * where (xxx,yyy) in ((?,?),(?,?))
     *
     * @author liutangqi
     * @date 2025/3/1 14:11
     * @Param [rowConstructor]
     **/
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

    @Override
    public void visit(AllColumns allColumns) {

    }

    @Override
    public void visit(AllTableColumns allTableColumns) {

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

    @Override
    public void visit(ParenthesedSelect parenthesedSelect) {
    }

    /**
     * convert函数
     *
     * @author liutangqi
     * @date 2025/3/17 16:51
     * @Param [transcodingFunction]
     **/
    @Override
    public void visit(TranscodingFunction transcodingFunction) {
        //处理表达式
        Expression expression = transcodingFunction.getExpression();
        DBDecryptExpressionVisitor dbDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this);
        expression.accept(dbDecryptExpressionVisitor);

        //处理后的表达式赋值
        transcodingFunction.setExpression(Optional.ofNullable(dbDecryptExpressionVisitor.getProcessedExpression()).orElse(expression));
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
