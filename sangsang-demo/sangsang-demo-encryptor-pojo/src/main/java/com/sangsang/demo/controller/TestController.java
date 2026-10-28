package com.sangsang.demo.controller;

import com.sangsang.demo.domain.dto.UserUpdateDto;
import com.sangsang.demo.domain.vo.UserResultMapVo;
import com.sangsang.demo.domain.vo.UserResultTypeVo;
import com.sangsang.demo.domain.vo.UserUnderLineVo;
import com.sangsang.demo.domain.vo.UserVo;
import com.sangsang.demo.mapper.PojoUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 一些测试用例
 *
 * @author liutangqi
 * @date 2026/10/28 15:15
 */
@RequiredArgsConstructor
@RestController
public class TestController {
    private final PojoUserMapper userMapper;

    /**
     * 验证参数批量加密后，仍会调用Mapper中配置的业务TypeHandler翻译密文
     */
    @PostMapping("/testCiphertextTranslation")
    public UserVo testCiphertextTranslation(@RequestBody UserUpdateDto dto) {
        userMapper.updatePhoneByCiphertextTranslation(dto);
        return userMapper.getUserById(dto.getId());
    }


    /**
     * 测试ResultType大小写随便写的映射
     *
     * @author liutangqi
     * @date 2026/8/14 16:10
     * @Param []
     **/
    @GetMapping("/testUserResultTypeList")
    public List<UserResultTypeVo> getUserResultTypeList() {
        return userMapper.getUserResultTypeList();
    }

    /**
     * 测试ResultMap特殊映射
     *
     * @author liutangqi
     * @date 2026/8/14 16:10
     * @Param []
     **/
    @GetMapping("/testUserResultMapList")
    public List<UserResultMapVo> getUserResultMapList() {
        return userMapper.getUserResultMapList();
    }

    /**
     * 测试下划线到下划线的映射
     *
     * @author liutangqi
     * @date 2026/8/14 17:00
     * @Param []
     **/
    @GetMapping("/testUserUnderLineList")
    public List<UserUnderLineVo> getUserUnderLineList() {
        return userMapper.getUserUnderLineList();
    }

    /**
     * 返回值是List<Map>
     *
     * @author liutangqi
     * @date 2026/8/21 15:31
     * @Param []
     **/
    @GetMapping("/getUserMapList")
    public List<Map<String, Object>> getUserMapList() {
        return userMapper.getUserMapList();
    }

    /**
     * 测试返回值是List<String>
     *
     * @author liutangqi
     * @date 2026/8/21 15:31
     * @Param []
     **/
    @GetMapping("/getUserPhoneList")
    public List<String> getUserPhoneList() {
        return userMapper.getUserPhoneList();
    }


    /**
     * 测试sql中存在注释，注释中存在占位符
     *
     * @author liutangqi
     * @date 2026/10/16 16:48
     * @Param []
     **/
    @GetMapping("/getPlaceholderPhoneList")
    public List<String> getPlaceholderPhoneList(@RequestParam(value = "userName", required = false) String userName) {
        return userMapper.getPlaceholderPhoneList(userName);
    }


    /**
     * 测试多一个入参对应xml中多个#{}的值
     *
     * @author liutangqi
     * @date 2026/10/28 15:17
     * @Param [phone, id]
     **/
    @GetMapping("/getMultipleMappingsUserList")
    public List<UserVo> getMultipleMappingsUserList() {
        return userMapper.getMultipleMappingsUserList("不存在的电话号码", 999999L);
    }
}
