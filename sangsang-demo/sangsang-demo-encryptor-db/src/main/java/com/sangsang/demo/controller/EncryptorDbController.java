package com.sangsang.demo.controller;

import com.sangsang.demo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author liutangqi
 * @date 2026/3/30 18:02
 */
@RestController
@RequiredArgsConstructor
public class EncryptorDbController {
    private final UserMapper userMapper;

    @GetMapping("/getUserListByPhone")
    public Object getUserListByPhone() {
        String phone = "1072901252";
        return userMapper.getUserListByPhone(phone);
    }
}
