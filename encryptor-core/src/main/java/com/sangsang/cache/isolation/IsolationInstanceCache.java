package com.sangsang.cache.isolation;

import cn.hutool.core.collection.ListUtil;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.config.other.DefaultBeanPostProcessor;
import com.sangsang.config.properties.FieldProperties;
import com.sangsang.domain.annos.isolation.DataIsolation;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.dto.ClasssCacheKey;
import com.sangsang.domain.enums.IsolationRelationEnum;
import com.sangsang.domain.exception.IsolationException;
import com.sangsang.domain.strategy.DefaultStrategyBase;
import com.sangsang.domain.strategy.isolation.DataIsolationStrategy;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.ExpressionsUtil;
import com.sangsang.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据隔离相关缓存
 * 优先加载这个bean，避免有些@PostConstruct 处理逻辑中需要用到这个缓存，但是这个缓存还未初始化完成
 *
 * @author liutangqi
 * @date 2025/6/13 10:29
 */
@Slf4j
public class IsolationInstanceCache extends DefaultBeanPostProcessor {
    /**
     * 缓存当前数据隔离实现类实例
     * key: 实现类的className
     * value:实例
     *
     * @author liutangqi
     * @date 2025/6/13 10:31
     * @Param
     **/
    private static final Map<ClasssCacheKey, DataIsolationStrategy> INSTANCE_MAP = new HashMap<>();

    /**
     * 初始化
     *
     * @author liutangqi
     * @date 2025/6/13 10:33
     * @Param []
     **/
    public void init(FieldProperties fieldProperties,
                     List<DataIsolationStrategy> dataIsolationStrategyList) throws Exception {
        long startTime = System.currentTimeMillis();
        //1.配置校验
        if (CollectionUtils.isEmpty(fieldProperties.getScanEntityPackage())) {
            throw new IsolationException("未配置扫描路径，请检查配置 field.scanEntityPackage");
        }

        //2.实例化默认的策略
        DefaultStrategyBase.BeanIsolationStrategy isolationBeanStrategy = new DefaultStrategyBase.BeanIsolationStrategy(dataIsolationStrategyList);
        INSTANCE_MAP.put(ClasssCacheKey.buildKey(DefaultStrategyBase.BeanIsolationStrategy.class), isolationBeanStrategy);

        //3.初始化当前spring容器内的实现策略
        for (DataIsolationStrategy dataIsolationStrategy : dataIsolationStrategyList) {
            INSTANCE_MAP.put(ClasssCacheKey.buildKey(dataIsolationStrategy.getClass()), dataIsolationStrategy);
        }

        log.debug("【isolation】初始化完毕，耗时:{}ms", (System.currentTimeMillis() - startTime));
    }

    //--------------------------------------------下面是对外提供的方法---------------------------------------------------------------

    /**
     * 从当前缓存中获取策略实例，如果获取不到，尝试使用反射实例化，并缓存到本地
     *
     * @author liutangqi
     * @date 2025/8/25 17:02
     * @Param [cls]
     **/
    public static DataIsolationStrategy getInstance(Class<? extends DataIsolationStrategy> clazz) {
        //1.先从本地缓存好的里面找
        DataIsolationStrategy instance = INSTANCE_MAP.get(ClasssCacheKey.buildKey(clazz));

        //2.本地缓存找不到，根据无参构造进行实例化，然后放缓存
        if (instance == null) {
            try {
                instance = clazz.newInstance();
                INSTANCE_MAP.put(ClasssCacheKey.buildKey(clazz), instance);
            } catch (Exception e) {
                throw new IsolationException(String.format("数据隔离策略无参构造实例化失败 %s", clazz.getName()));
            }
        }
        return instance;
    }

    /**
     * 根据表名得到获取当前登录的数据隔离信息
     *
     * @author liutangqi
     * @date 2025/6/13 10:58
     * @Param [tableName]
     **/
    public static List<DataIsolationStrategy> getInstance(String tableName) {
        DataIsolation dataIsolation = TableCache.getTableIsolationInfo().get(tableName);
        if (dataIsolation == null) {
            return null;
        }

        return Arrays.stream(dataIsolation.value()).map(m -> IsolationInstanceCache.getInstance(m)).collect(Collectors.toList());
    }

    /**
     * 根据表名获取表名头上面的@DataIsolation
     *
     * @author liutangqi
     * @date 2025/8/15 16:38
     * @Param [tableName]
     **/
    public static DataIsolation getDataIsolationByTableName(String tableName) {
        return TableCache.getTableIsolationInfo().get(tableName);
    }


    /**
     * 构建权限过滤的表达式
     *
     * @author liutangqi
     * @date 2025/6/13 13:10
     * @Param [isolationField：字段名, tableAlias: 字段所属表别名, isolationRelationEnum ：逻辑关系, isolationValue：数据隔离值]
     **/
    public static Expression buildIsolationExpression(String isolationField, String tableAlias, IsolationRelationEnum isolationRelationEnum, Object isolationValue) {
        //1.类型校验
        if (!DataIsolationStrategy.ALLOW_TYPES.stream().filter(f -> f.isAssignableFrom(isolationValue.getClass())).findAny().isPresent()) {
            throw new IsolationException(String.format("数据隔离值类型不支持 %s", isolationValue.getClass().getName()));
        }

        //2.拼凑字段
        Column column = new Column(isolationField);
        if (StringUtils.isNotBlank(tableAlias)) {
            column.setTable(new Table(tableAlias));
        }

        //3.拼凑值
        Expression valueExpression = null;
        List<ParenthesedExpressionList> inExpressionList = new ArrayList<>();
        //3.1  避免in 后面的值过大导致sql执行报错，这里进行分段
        if (isolationValue instanceof List) {
            Integer subsectionSize = TableCache.getCurConfig().getIsolation().getInRelationSubsectionSize();
            List<List> isolationValueList = ListUtil.split((List) isolationValue, subsectionSize);
            for (List iValue : isolationValueList) {
                List expressionList = ExpressionsUtil.buildExpressionList(iValue);
                ParenthesedExpressionList parenthesedExpressionList = new ParenthesedExpressionList();
                parenthesedExpressionList.addAll(expressionList);
                inExpressionList.add(parenthesedExpressionList);
            }
        }
        //3.2 其它类型，直接构建表达式
        else {
            valueExpression = ExpressionsUtil.buildConstant(isolationValue);
        }

        //4.拼凑表达式
        // field = value
        if (IsolationRelationEnum.EQUALS.equals(isolationRelationEnum)) {
            EqualsTo equalsTo = new EqualsTo();
            equalsTo.setLeftExpression(column);
            equalsTo.setRightExpression(valueExpression);
            return equalsTo;
        }

        //field like 'value%'
        if (IsolationRelationEnum.LIKE_PREFIX.equals(isolationRelationEnum)) {
            LikeExpression likeExpression = new LikeExpression();
            likeExpression.setLeftExpression(column);
            likeExpression.setRightExpression(new StringValue(isolationValue + SymbolConstant.PER_CENT));
            return likeExpression;
        }

        // field in (xxx,xxx)
        if (IsolationRelationEnum.IN.equals(isolationRelationEnum)) {
            //将分段in 的每一段构建成一个 in
            List<Expression> inExpressions = inExpressionList.stream()
                    .map(m -> ExpressionsUtil.buildInExpression(column, m))
                    .collect(Collectors.toList());
            //将不同段的in 使用 or 分隔开
            Expression orInExp = ExpressionsUtil.buildOrExpression(inExpressions);
            //多个in的话，使用括号包裹起来
            if (inExpressions.size() > 1) {
                return ExpressionsUtil.buildParenthesis(orInExp);
            }
            //只有一个in 返回这个表达式
            return orInExp;
        }

        throw new IsolationException(String.format("错误的数据隔离关系 %s", isolationRelationEnum));
    }
}
