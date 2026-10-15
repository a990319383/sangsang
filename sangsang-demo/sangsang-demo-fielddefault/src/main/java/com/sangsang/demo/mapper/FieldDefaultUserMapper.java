package com.sangsang.demo.mapper;

import com.sangsang.demo.domain.dto.UserQueryDto;
import com.sangsang.demo.domain.dto.UserSaveDto;
import com.sangsang.demo.domain.dto.UserUpdateDto;
import com.sangsang.demo.domain.vo.UserVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FieldDefaultUserMapper {
    List<UserVo> getUserList(UserQueryDto dto);
    Long countUserList(UserQueryDto dto);
    UserVo getUserById(@Param("id") Long id);
    int insertUser(UserSaveDto dto);
    int updateUser(UserUpdateDto dto);
    int deleteUserById(@Param("id") Long id);
}
