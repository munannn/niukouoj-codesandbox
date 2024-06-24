package com.luo.niukouojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.luo.niukouojcodesandbox.model.ExecuteCodeRequest;
import com.luo.niukouojcodesandbox.model.ExecuteCodeResponse;
import com.luo.niukouojcodesandbox.model.ExecuteMessage;
import com.luo.niukouojcodesandbox.model.JudgeInfo;
import com.luo.niukouojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

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
 * @Description 使用模板模式来优化代码沙箱的重复代码
 */
@Slf4j
public abstract class CodeSandBoxTemplate implements CodeSandBox {

    private static final String GLOBAL_DIR_PATH = "tempCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long TIME_OUT = 5000L;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        List<String> inputCaseList = executeCodeRequest.getInputCaseList();

        // 1.将用户的代码保存为文件
        File userCodeFile = saveCodeToFile(code);

        // 2.编译代码，获取编译信息
        ExecuteMessage executeMessage = compileFile(userCodeFile);
        System.out.println(executeMessage);

        // 3.执行编译代码，获取执行信息
        List<ExecuteMessage> executeMessageList = runCompileFile(userCodeFile, inputCaseList);

        //4. 在执行信息中收集输出结果
        ExecuteCodeResponse executeCodeResponse = getExecuteCodeResponse(executeMessageList);

        // 5.文件清理
        boolean del = delFile(userCodeFile);
        if (!del) {
            log.error("删除文件失败，userCodeFilePath:{}", userCodeFile.getParentFile().getAbsolutePath());
        }
        return executeCodeResponse;
    }

    /**
     * 1.将用户的代码保存为文件
     *
     * @param code
     * @return
     */
    public File saveCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePath = userDir + File.separator + GLOBAL_DIR_PATH;
        // 判断全局代码目录是否存在，不存在则创建
        if (!FileUtil.exist(globalCodePath)) {
            FileUtil.mkdir(globalCodePath);
        }
        // 将用户的代码隔离存放
        String userCodeParentPath = globalCodePath + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2.编译代码，获取编译信息
     *
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileFile(File userCodeFile) {
        String userCodePath = userCodeFile.getAbsolutePath();
        String cmdCompileCommand = String.format("javac -encoding utf-8 %s", userCodePath);
        try {
            // 通过运行时对象执行cmd指令得到进程对象
            Process compileProcess = Runtime.getRuntime().exec(cmdCompileCommand);
            ExecuteMessage executeMessage = ProcessUtils.getProcessExecuteMessage(compileProcess, "编译");
            return executeMessage;
        } catch (IOException e) {
            throw new RuntimeException("编译代码失败");
        }
    }

    /**
     * 3.执行编译代码，获取执行信息
     *
     * @return
     */
    public List<ExecuteMessage> runCompileFile(File userCodeFile, List<String> inputCaseList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputCaseArgs : inputCaseList) {
            String cmdExecuteCommand = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s",
                    userCodeParentPath, inputCaseArgs);
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
                throw new RuntimeException("执行代码失败");
            }
        }
        return executeMessageList;
    }

    /**
     * 4. 在执行信息中收集输出结果
     *
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getExecuteCodeResponse(List<ExecuteMessage> executeMessageList) {
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
        // 从Process中获取程序执行内存非常麻烦，暂不实现
        judgeInfo.setMemory(0L);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5.文件清理
     *
     * @param userCodeFile
     * @return
     */
    public boolean delFile(File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        if (userCodeFile.getParentFile() != null) {
            return FileUtil.del(userCodeParentPath);
        }
        return true;
    }

    /**
     * 6.错误处理
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
