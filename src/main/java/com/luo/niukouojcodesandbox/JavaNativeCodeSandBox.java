package com.luo.niukouojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.luo.niukouojcodesandbox.model.ExecuteCodeRequest;
import com.luo.niukouojcodesandbox.model.ExecuteCodeResponse;
import com.luo.niukouojcodesandbox.model.JudgeInfo;
import com.luo.niukouojcodesandbox.model.ExecuteMessage;
import com.luo.niukouojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @author 木南
 * @version 1.0
 * @Description Java原生代码沙箱
 */
public class JavaNativeCodeSandBox implements CodeSandBox {

    private static final String GLOBAL_DIR_PATH = "tempCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long TIME_OUT = 5000L;

    public static void main(String[] args) {
        JavaNativeCodeSandBox javaNativeCodeSandBox = new JavaNativeCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setLanguage("java");
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputCaseList(Arrays.asList("1 4", "3 9"));
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();
        List<String> inputCaseList = executeCodeRequest.getInputCaseList();


        String userDir = System.getProperty("user.dir");
        String globalCodePath = userDir + File.separator + GLOBAL_DIR_PATH;
        // 判断全局代码目录是否存在，不存在则创建
        if (!FileUtil.exist(globalCodePath)) {
            FileUtil.mkdir(globalCodePath);
        }
        // 1.将用户的代码隔离存放
        String userCodeParentPath = globalCodePath + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        // 2.编译代码，得到.class文件
        String cmdCompileCommand = String.format("javac -encoding utf-8 %s", userCodePath);
        try {
            // 通过运行时对象执行cmd指令得到进程对象
            Process compileProcess = Runtime.getRuntime().exec(cmdCompileCommand);
            ExecuteMessage executeMessage = ProcessUtils.getProcessExecuteMessage(compileProcess, "编译");
            System.out.println(executeMessage);
        } catch (IOException e) {
            return getErrorResponse(e);
        }
        // 3.执行编译后的代码，得到输出结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputCaseArgs : inputCaseList) {
            String cmdExecuteCommand = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s",
                    userCodeParentPath,
                    inputCaseArgs);
            try {
                Process executeProcess = Runtime.getRuntime().exec(cmdExecuteCommand);
                // 超时控制
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("超时了，中断");
                        executeProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.getProcessExecuteMessage(executeProcess,
                        "运行");
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                return getErrorResponse(e);
            }
        }
        //4. 收集输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        ArrayList<String> outputCaseList = new ArrayList<>();
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            // 判断进程执行信息中是否有错误信息
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                // 用户提交的代码存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputCaseList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(time, maxTime);
            }
        }
        // 正常运行完成且不存在错误信息，则表示题目通过，设置题目状态为1，成功
        if (outputCaseList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputCaseList(outputCaseList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        // 从Process中获取程序执行内存非常麻烦，实现不了
        // judgeInfo.setMemory();
        executeCodeResponse.setJudgeInfo(judgeInfo);
        // 5.文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }

    /**
     * 错误处理
     *
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        executeCodeResponse.setOutputCaseList(new ArrayList<>());
        return executeCodeResponse;
    }
}
