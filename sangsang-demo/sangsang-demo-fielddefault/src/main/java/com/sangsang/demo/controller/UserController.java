package com.sangsang.demo.controller;

import com.sangsang.demo.domain.bo.SqlLogBo;
import com.sangsang.demo.domain.dto.UserQueryDto;
import com.sangsang.demo.domain.dto.UserSaveDto;
import com.sangsang.demo.domain.dto.UserUpdateDto;
import com.sangsang.demo.domain.vo.UserListVo;
import com.sangsang.demo.domain.vo.UserVo;
import com.sangsang.demo.mapper.FieldDefaultUserMapper;
import com.sangsang.demo.threadlocal.SqlHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final FieldDefaultUserMapper userMapper;

    @GetMapping("/list")
    public UserListVo list(UserQueryDto dto) {
        List<UserVo> users = userMapper.getUserList(dto);
        Long total = userMapper.countUserList(dto);
        List<SqlLogBo> sqlLogs = SqlHolder.getSqls();
        return new UserListVo(users, sqlLogs, total, dto.getPageNum(), dto.getPageSize());
    }

    @GetMapping("/get/{id}")
    public UserVo get(@PathVariable Long id) {
        return userMapper.getUserById(id);
    }

    @PostMapping("/save")
    public UserListVo save(@RequestBody UserSaveDto dto) {
        userMapper.insertUser(dto);
        return list(new UserQueryDto());
    }

    @PostMapping("/update")
    public UserListVo update(@RequestBody UserUpdateDto dto) {
        userMapper.updateUser(dto);
        return list(new UserQueryDto());
    }

    @PostMapping("/delete/{id}")
    public UserListVo delete(@PathVariable Long id) {
        userMapper.deleteUserById(id);
        return list(new UserQueryDto());
    }
}
