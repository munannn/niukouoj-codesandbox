package com.luo.niukouojcodesandbox;

import com.luo.niukouojcodesandbox.model.ExecuteCodeRequest;
import com.luo.niukouojcodesandbox.model.ExecuteCodeResponse;

/**
 * @author 木南
 * @version 1.0
 * @Description 代码沙箱接口
 */
public interface CodeSandBox {

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
