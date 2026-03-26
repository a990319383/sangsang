package com.sangsang.domain.context;

import com.sangsang.domain.annos.isolation.ForbidIsolation;
import com.sangsang.util.CollectionUtils;

import java.util.ArrayDeque;

/**
 * @author liutangqi
 * @date 2025/6/16 8:51
 */
public class IsolationHolder {
    /**
     * 存储当前方法是否禁止了数据隔离的标识
     * 这里使用队列，存储标识，就可以支持嵌套
     **/
    private static final InheritableThreadLocal<ArrayDeque<ForbidIsolation>> ISOLATION_HOLDER = new InheritableThreadLocal<>();


    /**
     * 设置当前禁止数据隔离的标识
     *
     * @author liutangqi
     * @date 2025/6/16 9:06
     * @Param [forbidIsolation]
     **/
    public static void setForbidIsolation(ForbidIsolation forbidIsolation) {
        if (forbidIsolation == null) {
            return;
        }

        ArrayDeque<ForbidIsolation> forbidIsolations = ISOLATION_HOLDER.get();
        if (forbidIsolations == null) {
            forbidIsolations = new ArrayDeque<>();
            ISOLATION_HOLDER.set(forbidIsolations);
        }
        //从头部添加
        forbidIsolations.addFirst(forbidIsolation);
    }

    /**
     * 解除当前层禁止数据隔离标识
     *
     * @author liutangqi
     * @date 2025/6/16 9:06
     * @Param []
     **/
    public static void removeForbidIsolation() {
        ArrayDeque<ForbidIsolation> forbidIsolations = ISOLATION_HOLDER.get();
        if (CollectionUtils.isNotEmpty(forbidIsolations)) {
            //头部移除
            forbidIsolations.removeFirst();
            //如果移除完毕了，就整个清除了
            if (forbidIsolations.isEmpty()) {
                ISOLATION_HOLDER.remove();
            }
        }
    }

    /**
     * 获取当前方法是否禁止数据隔离
     *
     * @author liutangqi
     * @date 2025/6/16 17:48
     * @Param []
     **/
    public static ForbidIsolation getForbidIsolation() {
        ArrayDeque<ForbidIsolation> forbidIsolations = ISOLATION_HOLDER.get();
        if (CollectionUtils.isEmpty(forbidIsolations)) {
            return null;
        }
        //当前层存在禁止数据隔离标识则表示禁止，这里随便返回第一个即可
        return forbidIsolations.getFirst();
    }

}
