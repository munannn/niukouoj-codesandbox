package com.luo.niukouojcodesandbox;

import com.luo.niukouojcodesandbox.model.ExecuteCodeRequest;
import com.luo.niukouojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

/**
 * @author 木南
 * @version 1.0
 * @Description Java原生代码沙箱，直接复用模板方法
 */
@Component
public class JavaNativeCodeSandBox extends CodeSandBoxTemplate {

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
