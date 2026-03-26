package com.sangsang.transformation;

/**
 * 所有转换器的最顶级父类
 * 不同类型的基类转换器实现这个接口，具体的转换器实现不同类型的基类转换器
 * 注意1：不同的基类转换器的泛型T 必须不同，相同的泛型必须归属到同一个基类转换器中
 * 注意2：同一个类型的转换器实例，只会执行满足条件的其中一个
 *
 * @author liutangqi
 * @date 2025/5/21 10:10
 */
public interface TransformationInterface<T> {
    /**
     * 是否需要转换
     *
     * @author liutangqi
     * @date 2025/5/21 10:11
     * @Param [t]
     **/
    boolean needTransformation(T t);

    /**
     * 开始转换
     * 注意：这里最好有调整后，在原对象t上做调整，然后返回t对象，这样可以避免遗漏某些属性
     *
     * @author liutangqi
     * @date 2025/5/21 10:12
     * @Param [t]
     **/
    T doTransformation(T t);
}
