package com.luo.niukouojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.luo.niukouojcodesandbox.model.ExecuteCodeRequest;
import com.luo.niukouojcodesandbox.model.ExecuteCodeResponse;
import com.luo.niukouojcodesandbox.model.JudgeInfo;
import com.luo.niukouojcodesandbox.model.ExecuteMessage;
import com.luo.niukouojcodesandbox.utils.ProcessUtils;

import java.io.Closeable;
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
 * @Description Docker代码沙箱
 */
public class JavaDockerCodeSandBox implements CodeSandBox {

    private static final String GLOBAL_DIR_PATH = "tempCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long TIME_OUT = 5000L;
    private static Boolean FIRST_PULL = true;

    public static void main(String[] args) {
        JavaDockerCodeSandBox javaNativeCodeSandBox = new JavaDockerCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setLanguage("java");
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
        // 构建默认的DockerClient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 拉取docker hub上现成的轻量级Java运行环境镜像
        String image = "openjdk:8-alpine";
        // 只拉取一次
        if (FIRST_PULL) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            FIRST_PULL = false;
        }
        System.out.println("拉取镜像完成");

        // 3.创建docker容器，将编译后的文件上传到容器环境中
        CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(image);
        // 配置容器主机参数
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L)
                .withCpuCount(1L)
                .setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        // 允许在容器运行时从外部向容器输入数据、从容器获取标准（错误）输出，启用tty伪终端，使容器支持交互式终端操作
        CreateContainerResponse containerResponse = createContainerCmd.withHostConfig(hostConfig)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec();
        System.out.println(containerResponse);
        String containerId = containerResponse.getId();
        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        // docker exec keen_blackwell java -cp /app Main 1 3
        // 4.在容器中执行代码，获取输出结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputCase : inputCaseList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputCaseArray = inputCase.split(" ");
            // 拼接执行命令
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "app", "Main"}, inputCaseArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time;
            // 判断是否超时
            final boolean[] timeout = {true};
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果：" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果：" + message[0]);
                    }
                    super.onNext(frame);
                }

                @Override
                public void onComplete() {
                    // 如果执行完成，则表示没超时
                    timeout[0] = false;
                    super.onComplete();
                }
            };
            // 获取占用的内存
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    Long memory = statistics.getMemoryStats().getUsage();
                    if (memory != null) {
                        System.out.println("内存占用：" + memory);
                        maxMemory[0] = Math.max(maxMemory[0], memory);
                    }
                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });
            statsCmd.exec(statisticsResultCallback);
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion();
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }
        // 5.收集输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
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
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(time, maxTime);
            }
        }
        // 正常运行完成且不存在错误信息，则表示题目通过，设置题目状态为1，成功
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputCaseList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        //6. 文件清理
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
