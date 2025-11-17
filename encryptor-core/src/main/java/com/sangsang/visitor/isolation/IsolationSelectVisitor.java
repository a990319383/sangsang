package com.sangsang.visitor.isolation;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.cache.isolation.IsolationInstanceCache;
import com.sangsang.domain.annos.isolation.DataIsolation;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.enums.IsolationConditionalRelationEnum;
import com.sangsang.domain.strategy.isolation.DataIsolationStrategy;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.ExpressionsUtil;
import com.sangsang.util.StringUtils;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author liutangqi
 * @date 2025/6/13 13:41
 */
public class IsolationSelectVisitor extends BaseFieldParseTable implements SelectVisitor {

    /**
     * 获取当前层实例
     *
     * @author liutangqi
     * @date 2025/6/13 13:43
     * @Param [baseFieldParseTable]
     **/
    public static IsolationSelectVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new IsolationSelectVisitor(baseFieldParseTable.getLayer(), baseFieldParseTable.getLayerSelectTableFieldMap(), baseFieldParseTable.getLayerFieldTableMap());
    }

    /**
     * 获取下一层的实例
     *
     * @author liutangqi
     * @date 2025/6/13 14:34
     * @Param [baseFieldParseTable]
     **/
    public static IsolationSelectVisitor newInstanceNextLayer(BaseFieldParseTable baseFieldParseTable) {
        return new IsolationSelectVisitor(baseFieldParseTable.getLayer() + 1, baseFieldParseTable.getLayerSelectTableFieldMap(), baseFieldParseTable.getLayerFieldTableMap());
    }

    private IsolationSelectVisitor(int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
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
     * @date 2025/6/13 13:48
     * @Param [parenthesedSelect]
     **/
    @Override
    public void visit(ParenthesedSelect parenthesedSelect) {
        //注意：这里层数是当前层，这个的解析结果需要和外层在同一层级
        Optional.ofNullable(parenthesedSelect.getSelect()).ifPresent(p -> p.accept(IsolationSelectVisitor.newInstanceCurLayer(this)));
    }

    /**
     * 普通的select 查询
     *
     * @author liutangqi
     * @date 2025/6/13 13:49
     * @Param [plainSelect]
     **/
    @Override
    public void visit(PlainSelect plainSelect) {
        //1.处理from的表（只处理嵌套查询）
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem != null) {
            fromItem.accept(IsolationFromItemVisitor.newInstanceCurLayer(this));
        }

        //2.处理selectItem中属于子查询的字段
        List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
        if (CollectionUtils.isNotEmpty(selectItems)) {
            for (SelectItem<?> selectItem : selectItems) {
                selectItem.accept(IsolationSelectItemVisitor.newInstanceCurLayer(this));
            }
        }

        //3.处理where条件（主要针对in 子查询和exist）
        Expression where = plainSelect.getWhere();
        if (where != null) {
            where.accept(IsolationExpressionVisitor.newInstanceCurLayer(this));
        }

        //4.处理当前层的数据隔离
        //4.1.存储当前拼接的权限过滤条件(这里list存储的是不同的表的隔离字段)
        List<Expression> isolationExpressions = new ArrayList<>();
        //4.2.获取当前层字段信息
        Map<String, Set<FieldInfoDto>> fieldTableMap = this.getLayerFieldTableMap().get(String.valueOf(this.getLayer()));

        //4.3.判断其中是否存在数据隔离的表
        for (Map.Entry<String, Set<FieldInfoDto>> fieldTableEntry : fieldTableMap.entrySet()) {
            //4.3.1 随便获取一个字段，得到这个字段所属的真实表名（因为这些字段都是属于同一张真实表，所以随便获取一个即可），如果这个表所属的表不是来源真实表则直接跳过
            FieldInfoDto anyFieldInfo = fieldTableEntry.getValue().stream().findAny().orElse(null);
            if (anyFieldInfo == null || !anyFieldInfo.isFromSourceTable() || StringUtils.isBlank(anyFieldInfo.getSourceTableName())) {
                continue;
            }
            //4.3.2 通过表名获取到当前的表隔离的相关信息（外层获取，避免方法重复调用），不需要隔离则跳过这个表
            List<DataIsolationStrategy> dataIsolationStrategies = IsolationInstanceCache.getInstance(anyFieldInfo.getSourceTableName());
            DataIsolation dataIsolation = IsolationInstanceCache.getDataIsolationByTableName(anyFieldInfo.getSourceTableName());
            if (CollectionUtils.isEmpty(dataIsolationStrategies) || dataIsolation == null) {
                continue;
            }

            //4.3.3 当前表可能存在多个隔离策略，将其格式转换为key是表字段，value是隔离字段
            Map<String, List<DataIsolationStrategy>> isolationFieldMap = new FieldHashMapWrapper<>();
            dataIsolationStrategies.stream()
                    .filter(f -> StringUtils.isNotBlank(f.getIsolationField(anyFieldInfo.getSourceTableName())))
                    .forEach(f -> {
                        List<DataIsolationStrategy> strategies = isolationFieldMap.getOrDefault(f.getIsolationField(anyFieldInfo.getSourceTableName()), new ArrayList<>());
                        strategies.add(f);
                        isolationFieldMap.put(f.getIsolationField(anyFieldInfo.getSourceTableName()), strategies);
                    });
            if (org.springframework.util.CollectionUtils.isEmpty(isolationFieldMap)) {
                continue;
            }

            //4.3.4 存储当前表字段的隔离条件拼凑的隔离条件
            List<Expression> tableIsolationExpressions = new ArrayList<>();

            //4.3.5 依次处理每个字段，判断这些字段是否需要数据隔离
            for (FieldInfoDto fieldInfo : fieldTableEntry.getValue()) {
                //4.3.5.1当前字段不是直接来自真实表的，跳过
                if (!fieldInfo.isFromSourceTable()) {
                    continue;
                }
                //4.3.5.2查看当前字段是否需要参与数据隔离
                List<DataIsolationStrategy> dataIsolationStrategy = isolationFieldMap.get(fieldInfo.getSourceColumn());
                if (CollectionUtils.isEmpty(dataIsolationStrategy)) {
                    continue;
                }
                //4.3.5.3开拼
                for (DataIsolationStrategy isolationStrategy : dataIsolationStrategy) {
                    Expression isolationExpression = IsolationInstanceCache.buildIsolationExpression(isolationStrategy.getIsolationField(anyFieldInfo.getSourceTableName())
                            , fieldTableEntry.getKey()
                            , isolationStrategy.getIsolationRelation(anyFieldInfo.getSourceTableName())
                            , isolationStrategy.getIsolationData(anyFieldInfo.getSourceTableName()));
                    //4.3.5.4 将拼接好的条件维护到这个表的隔离条件集合中
                    tableIsolationExpressions.add(isolationExpression);
                }
            }

            //4.3.6 处理这一张表不同的条件
            //4.3.6.1 这张表没有额外加隔离条件，或者只加了一个隔离条件，则不处理，只需要将这个隔离条件加到表级别的list中即可
            if (tableIsolationExpressions.size() <= 1) {
                isolationExpressions.addAll(tableIsolationExpressions);
                continue;
            }
            //4.3.6.1 单表不同字段之间是and
            if (IsolationConditionalRelationEnum.AND.equals(dataIsolation.conditionalRelation())) {
                isolationExpressions.add(ExpressionsUtil.buildAndExpression(tableIsolationExpressions));
            }
            //4.3.6.2 单表不同字段之间是or ，使用or拼接完成后，再将整个表达式使用括号包裹起来
            if (IsolationConditionalRelationEnum.OR.equals(dataIsolation.conditionalRelation())) {
                Expression tableIsoExp = ExpressionsUtil.buildOrExpression(tableIsolationExpressions);
                isolationExpressions.add(ExpressionsUtil.buildParenthesis(tableIsoExp));
            }
        }

        //5.没有需要额外新增的隔离字段吗，则不处理
        if (CollectionUtils.isEmpty(isolationExpressions)) {
            return;
        }

        //6.处理不同表之间的条件关联关系
        Expression whereIsolation = null;
        if (IsolationConditionalRelationEnum.AND.equals(TableCache.getCurConfig().getIsolation().getConditionalRelation())) {
            whereIsolation = ExpressionsUtil.buildAndExpression(isolationExpressions);
        }
        if (IsolationConditionalRelationEnum.OR.equals(TableCache.getCurConfig().getIsolation().getConditionalRelation())) {
            whereIsolation = ExpressionsUtil.buildOrExpression(isolationExpressions);
        }

        //7.处理where条件
        //7.1 旧sql不存在where
        if (plainSelect.getWhere() == null) {
            plainSelect.setWhere(whereIsolation);
        }
        //7.2 旧sql存在where， 旧的表达式的关系和现在肯定是and的关系，将旧表达用括号包起来，否则里面存在or的话会有语义错误
        else {
            //7.2.1 旧的where 表达式中可能存在or的话，则使用括号包裹起来
            Expression plainSelectWhere = plainSelect.getWhere();
            if (!(plainSelectWhere instanceof Parenthesis) && !StringUtils.notExist(plainSelectWhere.toString(), "or")) {
                plainSelectWhere = ExpressionsUtil.buildParenthesis(plainSelectWhere);
            }
            //7.2.2 额外新增的隔离条件中可能存在or的话，使用括号包裹起来
            if (!(whereIsolation instanceof Parenthesis) && !StringUtils.notExist(whereIsolation.toString(), "or")) {
                whereIsolation = ExpressionsUtil.buildParenthesis(whereIsolation);
            }
            //7.2.3 将旧的where表达式和额外增加的隔离表达式使用and拼接
            AndExpression whereExpression = ExpressionsUtil.buildAndExpression(plainSelectWhere, whereIsolation);
            plainSelect.setWhere(whereExpression);
        }
    }

    /**
     * union
     *
     * @author liutangqi
     * @date 2025/6/13 13:53
     * @Param [setOpList]
     **/
    @Override
    public void visit(SetOperationList setOpList) {
        List<Select> selects = setOpList.getSelects();
        List<Select> resSelectBody = new ArrayList<>();
        for (int i = 0; i < selects.size(); i++) {
            Select select = selects.get(i);
            //解析每个union的语句自己拥有的字段信息
            FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
            select.accept(fieldParseTableSelectVisitor);

            //针对每个sql单独进行数据隔离处理
            IsolationSelectVisitor ilSelectVisitor = IsolationSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor);
            select.accept(ilSelectVisitor);

            //维护加解密之后的语句
            resSelectBody.add(select);
        }
        setOpList.setSelects(resSelectBody);
    }

    @Override
    public void visit(WithItem withItem) {

    }

    @Override
    public void visit(Values aThis) {

    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(TableStatement tableStatement) {

    }
}
