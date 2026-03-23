package com.sangsang.transformation.oracle2mysql.plainselect;

import com.sangsang.domain.dto.PlainSelectTransformationDto;
import com.sangsang.transformation.PlainSelectTransformation;
import com.sangsang.util.ExpressionsUtil;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * 对于oracle来说，子查询可以没有别名，但是mysql的子查询必须有别名，所以这里将缺少别名的子查询给增加上别名
 *
 * @author liutangqi
 * @date 2026/3/6 14:17
 */
public class AliasPlainSelectO2MTf extends PlainSelectTransformation {

    /**
     * select from的不是表
     *
     * @author liutangqi
     * @date 2026/3/9 9:52
     * @Param [pSTfDto]
     **/
    @Override
    public boolean needTransformation(PlainSelectTransformationDto pSTfDto) {
        return true;
    }

    /**
     * from的是一个子查询，并且不存在别名，就增加别名
     *
     * @author liutangqi
     * @date 2026/3/6 14:47
     * @Param [pSTfDto]
     **/
    @Override
    public PlainSelectTransformationDto doTransformation(PlainSelectTransformationDto pSTfDto) {
        PlainSelect plainSelect = pSTfDto.getPlainSelect();
        //select from (select) 这种语法，并且不存在别名
        if (plainSelect.getFromItem() instanceof ParenthesedSelect
                && plainSelect.getFromItem().getAlias() == null
                && ((ParenthesedSelect) plainSelect.getFromItem()).getSelect() instanceof PlainSelect
        ) {
            FromItem fromItem = plainSelect.getFromItem();
            //这里设置默认的别名，注意：这里的别名要求不带 as，虽然mysql可以正常解析，但是oracle不能解析，我们尽量转换为适应性强的语法
            fromItem.setAlias(new Alias("oracle2mysql_empty_alias", false));
        }
        return pSTfDto;
    }
}
