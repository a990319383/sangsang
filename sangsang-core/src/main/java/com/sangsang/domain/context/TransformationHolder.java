package com.sangsang.domain.context;

/**
 * @author liutangqi
 * @date 2025/5/21 18:07
 */
public class TransformationHolder {

    /**
     * 记录当前sql语句是否发生过语法转换
     */
    private static ThreadLocal<Boolean> TRANSFORMATION_HOLDER = new ThreadLocal();


    /**
     * 记录当前发生了语法转换
     *
     * @date 2025/5/21 18:08
     * @author liutangqi
     * @Param []
     **/
    public static void transformationRecord() {
        TRANSFORMATION_HOLDER.set(true);
    }

    /**
     * 判断当前是否发生了语法转换
     *
     * @author liutangqi
     * @date 2025/5/21 18:09
     * @Param []
     **/
    public static boolean isTransformation() {
        return TRANSFORMATION_HOLDER.get() != null && TRANSFORMATION_HOLDER.get();
    }

    /**
     * 手动清除上下文
     *
     * @author liutangqi
     * @date 2025/5/21 18:10
     * @Param []
     **/
    public static void clear() {
        TRANSFORMATION_HOLDER.remove();
    }
}
