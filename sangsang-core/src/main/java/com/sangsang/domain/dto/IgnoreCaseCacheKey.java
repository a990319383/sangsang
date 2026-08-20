
package com.sangsang.domain.dto;

import com.sangsang.domain.exception.FieldException;
import com.sangsang.util.StringUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

/**
 * 不管配置，忽略大小写的情况下，字符串相等就是相等
 *
 * @author liutangqi
 * @date 2026/8/18 16:51
 **/
@Getter
@Setter
@ToString
public class IgnoreCaseCacheKey implements Serializable {
    /**
     * 需要缓存的key
     */
    private String cacheKey;

    /**
     * 根据当前项目配置，会判断是否进行大小写敏感，并不考虑关键字，判断两者是否相等
     *
     * @author liutangqi
     * @date 2026/8/18 16:51
     * @Param [obj]
     **/
    @Override
    public boolean equals(Object obj) {
        //判断整个对象
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        //判断对象的cacheKey
        String thisCacheKey = this.getCacheKey();
        String objCacheKey = ((IgnoreCaseCacheKey) obj).getCacheKey();
        if (thisCacheKey == objCacheKey) return true;
        return StringUtils.ignoreCaseEquals(thisCacheKey, objCacheKey);
    }


    @Override
    public int hashCode() {
        //1.都转换为小写
        String key = this.getCacheKey().toLowerCase();

        //2.将处理好的key进行hashCode
        return Objects.hash(key);
    }

    /**
     * 私有化构造方法
     *
     * @author liutangqi
     * @date 2026/8/18 16:51
     * @Param [cacheKey]
     **/
    private IgnoreCaseCacheKey(String cacheKey) {
        if (StringUtils.isBlank(cacheKey)) {
            throw new FieldException("IgnoreCaseCacheKey 不能构建空字符串");
        }
        this.cacheKey = cacheKey;
    }

    /**
     * 构建缓存key
     *
     * @author liutangqi
     * @date 2026/8/18 16:51
     * @Param [cacheKey]
     **/
    public static IgnoreCaseCacheKey buildKey(String cacheKey) {
        return new IgnoreCaseCacheKey(cacheKey);
    }

}
