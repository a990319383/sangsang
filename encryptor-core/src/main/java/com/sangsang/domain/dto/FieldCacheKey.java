package com.sangsang.domain.dto;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.util.StringUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

/**
 * 数据库字段或者表名缓存Map中的key
 * 会根据当前项目的配置，如果大小写不敏感的话，则两个字符串忽略大小写相等就认为相等，并且忽略当前项目的关键字符号
 *
 * @author liutangqi
 * @date 2025/11/5 17:51
 */
@Getter
@Setter
@ToString
public class FieldCacheKey implements Serializable {
    /**
     * 需要缓存的key
     * 一般是数据库字段或者表名
     */
    private String cacheKey;

    /**
     * 根据当前项目配置，会判断是否进行大小写敏感，并不考虑关键字，判断两者是否相等
     *
     * @author liutangqi
     * @date 2025/11/5 17:58
     * @Param [obj]
     **/
    @Override
    public boolean equals(Object obj) {
        //判断整个对象
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        //判断对象的cacheKey
        String thisCacheKey = this.getCacheKey();
        String objCacheKey = ((FieldCacheKey) obj).getCacheKey();
        if (thisCacheKey == objCacheKey) return true;
        return StringUtils.fieldEquals(thisCacheKey, objCacheKey);
    }


    @Override
    public int hashCode() {
        String key = this.getCacheKey();
        //1.如果当前大小写不敏感，则都转换为小写
        if (!TableCache.getCurConfig().isCaseSensitive()) {
            key = key.toLowerCase();
        }

        //2.去除关键字标识符
        key = StringUtils.trimSymbol(key, TableCache.getCurConfig().getIdentifierQuote());

        //3.将处理好的key进行hashCode
        return Objects.hash(key);
    }

    /**
     * 私有化构造方法
     *
     * @author liutangqi
     * @date 2025/11/5 17:53
     * @Param [cacheKey]
     **/
    private FieldCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    /**
     * 构建缓存key
     *
     * @author liutangqi
     * @date 2025/11/5 17:53
     * @Param [cacheKey]
     **/
    public static FieldCacheKey buildKey(String cacheKey) {
        return new FieldCacheKey(cacheKey);
    }

}
