package com.luo.niukouojcodesandbox.controller;

import cn.hutool.crypto.digest.MD5;
import com.luo.niukouojcodesandbox.JavaNativeCodeSandBox;
import com.luo.niukouojcodesandbox.model.ExecuteCodeRequest;
import com.luo.niukouojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author 木南
 * @version 1.0
 */
@RestController
public class MainController {

    @Resource
    private JavaNativeCodeSandBox javaNativeCodeSandBox;

    private static final String AUTH_REQUEST_HEADER = "auth";
    private static final String AUTH_REQUEST_ACCESS = "access";

    @PostMapping("executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        String header = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_ACCESS.equals(header)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        return executeCodeResponse;
    }
}
