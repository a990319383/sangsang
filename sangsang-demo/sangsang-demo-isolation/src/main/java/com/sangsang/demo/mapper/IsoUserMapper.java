package com.sangsang.demo.mapper;

import com.sangsang.demo.domain.dto.UserQueryDto;
import com.sangsang.demo.domain.dto.UserSaveDto;
import com.sangsang.demo.domain.dto.UserUpdateDto;
import com.sangsang.demo.domain.vo.UserVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author liutangqi
 * @date 2026/3/30 17:54
 */
public interface IsoUserMapper {
    /**
     * 按用户名或电话模糊查询用户列表
     */
    List<UserVo> getUserList(UserQueryDto dto);

    /**
     * 统计用户总数
     */
    Long countUserList(UserQueryDto dto);

    /**
     * 根据ID查询用户
     */
    UserVo getUserById(@Param("id") Long id);

    /**
     * 新增用户
     */
    int insertUser(UserSaveDto dto);

    /**
     * 修改用户
     */
    int updateUser(UserUpdateDto dto);

    /**
     * 删除用户
     */
    int deleteUserById(@Param("id") Long id);
}
