package com.sangsang.transformation.oracle2mysql.plainselect;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.O2MTfExpressionDto;
import com.sangsang.domain.dto.PlainSelectTransformationDto;
import com.sangsang.transformation.PlainSelectTransformation;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.ExpressionsUtil;
import com.sangsang.visitor.transformation.oracle2mysql.O2MTfExpressionVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.List;

/**
 * oracle的分页语法转换
 * todo-ltq 分页的具体数据可能是外部传的？ 所以这里页签的转换应该用Expression函数来进行转换
 *
 * @author liutangqi
 * @date 2026/1/5 14:51
 */
public class PagePlainSelectO2MTf extends PlainSelectTransformation {
    @Override
    public boolean needTransformation(PlainSelectTransformationDto plainSelectTransformationDto) {
        return true;
    }

    @Override
    public PlainSelectTransformationDto doTransformation(PlainSelectTransformationDto psTfDto) {
        PlainSelect plainSelect = psTfDto.getPlainSelect();
        BaseFieldParseTable baseFieldParse = psTfDto.getBaseFieldParseTable();

        //1.判断select中是否存在行号字段，存在的话，将这个字段从结果集中移除
        List<SelectItem<?>> resSelectItems = new ArrayList<>();
        List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
        if (CollectionUtils.isNotEmpty(selectItems)) {
            for (SelectItem<?> selectItem : selectItems) {
                //1.1 创建对应的visitor
                O2MTfExpressionVisitor o2MTfExpressionVisitor = O2MTfExpressionVisitor.newInstanceCurLayer(selectItem.getAlias(), baseFieldParse);
                //1.2 处理当前表达式，判断是否需要保留
                selectItem.getExpression().accept(o2MTfExpressionVisitor);
                //1.3 当前表达式需要保留，则将当前表达式添加到结果集
                if (o2MTfExpressionVisitor.getProcess().isRetainExpression()) {
                    resSelectItems.add(selectItem);
                }
            }
        }

        //2. 将结果集的 selectItems 赋给 plainSelect
        plainSelect.setSelectItems(resSelectItems);

        //3.判断where条件中是否存在 行号字段大小与比较
        //注意： 这里只处理 between a and b 或者单纯的> >= < <= ，有其它复杂的嵌套条件说明不是单纯的分页条件
        Expression where = plainSelect.getWhere();
        if (where != null) {
            //3.1 创建对应visitor
            O2MTfExpressionVisitor o2MTfExpressionVisitor = O2MTfExpressionVisitor.newInstanceCurLayer(baseFieldParse);
            //3.2 处理where条件
            where.accept(o2MTfExpressionVisitor);
            O2MTfExpressionDto processRes = o2MTfExpressionVisitor.getProcess();
            //3.3 如果where条件不需要保留了，则置为null
            if (!processRes.isRetainExpression()) {
                plainSelect.setWhere(null);
            }
            //3.4 如果存在分页的条件的话，则将>= <= 转换为limit
            plainSelect.setLimit(ExpressionsUtil.buildLimit(processRes.getGe(), processRes.getLe()));
        }

        //4.改造后的select
        // 查询的是* 或者别名* && where条件不存在 && from的表是一个嵌套子查询，并且子查询是一个单纯的查询语句
        // 则表示这层嵌套没有用了，把这层嵌套去了
        List<SelectItem<?>> curSelectItems = plainSelect.getSelectItems();
        Expression curWhere = plainSelect.getWhere();
        FromItem fromItem = plainSelect.getFromItem();
        if ((curSelectItems != null && curSelectItems.size() == 1 && ((curSelectItems.get(0).getExpression() instanceof AllTableColumns) || (curSelectItems.get(0).getExpression() instanceof AllColumns)))
                && curWhere == null
                && fromItem instanceof ParenthesedSelect && ((ParenthesedSelect) fromItem).getSelect() instanceof PlainSelect
        ) {
            //4.1去掉的那层有limit的话，把limit挪位置，挪到当前层
            Limit upstreamLimit = plainSelect.getLimit();
            //4.2 去掉这层嵌套
            plainSelect = (PlainSelect) ((ParenthesedSelect) fromItem).getSelect();
            //4.3 如果当前层也存在Limit的话，将上层的Limit和本层的Lmit进行合并
            Limit curLimit = ExpressionsUtil.mergeLimit(upstreamLimit, plainSelect.getLimit());
            plainSelect.setLimit(curLimit);

            //4.4 由于sql少了一层，所以将解析结果往上挪一层 todo-ltq ??? 到时候看是否需要挪

        }

        return PlainSelectTransformationDto.builder().plainSelect(plainSelect).build();
    }

}
