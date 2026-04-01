package com.sangsang.demo.controller;

import com.sangsang.demo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端相关接口
 *
 * @author liutangqi
 * @date 2026/3/31 14:38
 */
@Controller
@RequiredArgsConstructor
public class IndexController {

    /**
     * 跳转到index.html
     *
     * @author liutangqi
     * @date 2026/3/31 14:38
     * @Param [model]
     **/
    @GetMapping("/index")
    public String index(Model model) {
        return "index";
    }

}
