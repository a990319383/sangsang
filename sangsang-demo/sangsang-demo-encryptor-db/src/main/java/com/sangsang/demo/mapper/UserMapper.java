package com.sangsang.demo.mapper;

import com.sangsang.demo.vo.UserVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author liutangqi
 * @date 2026/3/30 17:54
 */
public interface UserMapper {

    /**
     * sql的入参是需要密文存储的字段，查询的结果也有需要密文存储的字段
     *
     * @author liutangqi
     * @date 2026/3/30 17:55
     * @Param [phone]
     **/
    List<UserVo> getUserListByPhone(@Param("phone") String phone);
}
