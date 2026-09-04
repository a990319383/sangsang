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
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.StringUtils;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;

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

    private IsolationSelectVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
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
        // CTE 定义挂在 PlainSelect 上时，先处理 CTE 内部的真实表
        List<WithItem> withItems = plainSelect.getWithItemsList();
        if (CollectionUtils.isNotEmpty(withItems)) {
            for (WithItem withItem : withItems) {
                withItem.accept(this);
            }
        }

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

        //3.处理where条件
        Optional.ofNullable(JsqlparserUtil.isolationWhere(plainSelect.getWhere(), this))
                .ifPresent(p -> plainSelect.setWhere(p));
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
        // WITH 定义挂在 SetOperationList 上时，先处理 CTE 内部的真实表
        List<WithItem> withItems = setOpList.getWithItemsList();
        if (CollectionUtils.isNotEmpty(withItems)) {
            for (WithItem withItem : withItems) {
                withItem.accept(this);
            }
        }

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

    /**
     * CTE语法 栗如： WITH x AS (SELECT id,name FROM tb_user)
     * 这里括号内部的语法是一个完全独立的sql，不涉及访问外部作用域，所以单独处理即可
     *
     * @author liutangqi
     * @date 2026/9/4 14:16
     * @Param [withItem]
     **/
    @Override
    public void visit(WithItem withItem) {
        if (withItem.getSelect() == null) {
            return;
        }

        //因为是完全独立的sql，所以单独解析这层sql
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        withItem.getSelect().accept(fieldParseTableSelectVisitor);

        //利用解析好的结果集，对内部字段进行数据权限隔离处理
        IsolationSelectVisitor isolationSelectVisitor = IsolationSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor);
        withItem.getSelect().accept(isolationSelectVisitor);
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
