package com.sangsang.demo.controller;

import com.sangsang.demo.domain.bo.SqlLogBo;
import com.sangsang.demo.domain.dto.UserQueryDto;
import com.sangsang.demo.domain.dto.UserSaveDto;
import com.sangsang.demo.domain.dto.UserUpdateDto;
import com.sangsang.demo.domain.vo.UserListVo;
import com.sangsang.demo.mapper.DemoTransformationUserMapper;
import com.sangsang.demo.domain.vo.UserVo;
import com.sangsang.demo.threadlocal.SqlHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户增删改查接口
 *
 * @author liutangqi
 * @date 2026/3/31
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final DemoTransformationUserMapper userMapper;

    /**
     * 查询用户列表（支持按用户名/电话模糊搜索），同时返回 sangsang 改写前后的 SQL
     */
    @GetMapping("/list")
    public UserListVo list(UserQueryDto dto) {
        List<UserVo> users = userMapper.getUserList(dto);
        Long total = userMapper.countUserList(dto);
        List<SqlLogBo> sqlLogs = SqlHolder.getSqls();
        return new UserListVo(users, sqlLogs, total, dto.getPageNum(), dto.getPageSize());
    }

    /**
     * 根据ID查询用户
     */
    @GetMapping("/get/{id}")
    public UserVo get(@PathVariable Long id) {
        return userMapper.getUserById(id);
    }

    /**
     * 新增用户，并返回最新列表和 SQL 日志
     */
    @PostMapping("/save")
    public UserListVo save(@RequestBody UserSaveDto dto) {
        userMapper.insertUser(dto);
        return list(new UserQueryDto());
    }

    /**
     * 修改用户，并返回最新列表和 SQL 日志
     */
    @PostMapping("/update")
    public UserListVo update(@RequestBody UserUpdateDto dto) {
        userMapper.updateUser(dto);
        return list(new UserQueryDto());
    }

    /**
     * 删除用户，并返回最新列表和 SQL 日志
     */
    @PostMapping("/delete/{id}")
    public UserListVo delete(@PathVariable Long id) {
        userMapper.deleteUserById(id);
        return list(new UserQueryDto());
    }
}
