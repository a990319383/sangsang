package com.sangsang.cache.transformation;

import com.sangsang.config.other.DefaultBeanPostProcessor;
import com.sangsang.config.properties.SangSangProperties;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.context.TransformationHolder;
import com.sangsang.domain.wrapper.ClassHashMapWrapper;
import com.sangsang.transformation.TransformationInterface;
import com.sangsang.util.ClassScanerUtil;
import com.sangsang.util.GenericityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 各类转换器实例缓存
 * 优先加载这个bean，避免有些@PostConstruct 处理逻辑中需要用到这个缓存，但是这个缓存还未初始化完成
 *
 * @author liutangqi
 * @date 2025/5/21 16:24
 */
@Slf4j
public class TransformationInstanceCache extends DefaultBeanPostProcessor {
    /**
     * 存储当前转换器的实例
     * key:转换器的父类class (限定是属于Column 还是Function)
     * value:对应转换器实例
     */
    private static final Map<Class, List<TransformationInterface>> TRANSFORMATION_MAP = new ClassHashMapWrapper<>();
    /**
     * 实现了TransformationInterface接口的基类转换器的Class和泛型的对应关系
     * key:泛型Class
     * value:类的Class
     */
    private static final Map<Class, Class> SUPERTRANSFORMATION_MAP = new ClassHashMapWrapper<>();


    //---------------------------对外提供的方法分割线---------------------------

    /**
     * 初始化各种实例
     *
     * @author liutangqi
     * @date 2025/5/21 16:45
     * @Param [functionTransformationList, columnTransformationList, expressionTransformationList]
     **/
    public void init(SangSangProperties sangSangProperties) {
        //1.将TransformationInterface的包路径 + 当前转换类型作为包路径，扫描下面的所有的类
        String basePackage = TransformationInterface.class.getPackage().getName() + SymbolConstant.FULL_STOP + sangSangProperties.getTransformation().getPatternType();

        //2.扫描当前配置模式包路径下所有TransformationInterface的子类
        List<Class> classes = ClassScanerUtil.scan(basePackage).stream().filter(c -> TransformationInterface.class.isAssignableFrom(c)).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(classes)) {
            log.warn("【sangsang】<transformation>未扫描到转换器，请确保transformation.pattern配置的转换类型和存放转换器的包路径名一致");
        }

        //3.将对应的转换器子类实例化后存储到对应的缓存中
        for (Class tfClz : classes) {
            List<TransformationInterface> tfList = TRANSFORMATION_MAP.getOrDefault(tfClz.getSuperclass(), new ArrayList<>());
            tfList.add((TransformationInterface) ReflectUtils.newInstance(tfClz));
            TRANSFORMATION_MAP.put(tfClz.getSuperclass(), tfList);
        }

        //4.找到各个转换器父类泛型和对应转换器父类class的对应关系
        classes.stream().map(Class::getSuperclass).distinct().forEach(f -> SUPERTRANSFORMATION_MAP.put(GenericityUtil.getInterfaceT(f, 0), f));
    }

    /**
     * 进行数据转换
     *
     * @author liutangqi
     * @date 2025/5/23 17:08
     * @Param [t： 必须和转换器的泛型类型一致]
     **/
    public static <T> T transformation(T t) {
        //1.先找转换器类型基类中泛型是这个的
        Class superClass = SUPERTRANSFORMATION_MAP.get(t.getClass());
        if (superClass == null) {
            //不同的异构数据库之间，可能有的不需要某些转换器，所以找不到这里返回原样
            return t;
        }

        //2.找到这个基类转换器的所有实现类实例
        List<TransformationInterface> transformationList = TRANSFORMATION_MAP.get(superClass);

        //3.从所有实例中找符合处理条件的
        T res = t;
        for (TransformationInterface transformationInterface : transformationList) {
            if (transformationInterface.needTransformation(res)) {
                //3.1 记录当前进行过语法转换
                TransformationHolder.transformationRecord();
                //3.2 返回转换后的结果
                res = (T) transformationInterface.doTransformation(res);
            }
        }
        return res;
    }

    /**
     * 进行数据转换
     *
     * @author liutangqi
     * @date 2025/5/23 17:08
     * @Param [t： 不必转换器的泛型类型一致,typeClass:指定基类转换器泛型类型]
     **/
    public static <T> T transformation(T t, Class typeClass) {
        //1.先找转换器类型基类中泛型是这个的
        Class superClass = SUPERTRANSFORMATION_MAP.get(typeClass);
        if (superClass == null) {
            //不同的异构数据库之间，可能有的不需要某些转换器，所以找不到这里返回原样
            return t;
        }

        //2.找到这个基类转换器的所有实现类实例
        List<TransformationInterface> transformationList = TRANSFORMATION_MAP.get(superClass);

        //3.从所有实例中找符合处理条件的
        T res = t;
        for (TransformationInterface transformationInterface : transformationList) {
            if (transformationInterface.needTransformation(res)) {
                //3.1 记录当前进行过语法转换
                TransformationHolder.transformationRecord();
                //3.2 返回转换后的结果
                res = (T) transformationInterface.doTransformation(res);
            }
        }
        return res;
    }
}
