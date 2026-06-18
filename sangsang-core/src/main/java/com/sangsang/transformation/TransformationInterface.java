package com.sangsang.transformation;

/**
 * 所有转换器的最顶级父类
 * 不同类型的基类转换器实现这个接口，具体的转换器实现不同类型的基类转换器
 * 注意1：不同的基类转换器的泛型T 必须不同，相同的泛型必须归属到同一个基类转换器中
 * 注意2：同一个类型的转换器实例，会执行所有满足条件的
 *
 * @author liutangqi
 * @date 2025/5/21 10:10
 */
public interface TransformationInterface<T> {
    /**
     * 是否需要转换
     * 只要这个返回为true就标识这个语法需要转换，此时就不会将这个sql缓存到TransformationSqlCache中
     * 拦截器层，如果这个sql经历的所有转换器这个方法返回值都是false，则标识这个sql不会转换，则将这个sql缓存到TransformationSqlCache中，就不会走转换的耗时逻辑
     * 建议：复杂情况下，将对应转换器是否需要处理的逻辑都放在这个方法实现，否则会导致有对应语法的sql缓存失效，每次请求都走一遍解析转换的逻辑
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
