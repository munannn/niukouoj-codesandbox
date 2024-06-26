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
import com.luo.niukouojcodesandbox.model.ExecuteMessage;
import com.luo.niukouojcodesandbox.model.JudgeInfo;
import com.luo.niukouojcodesandbox.utils.ProcessUtils;
import com.sun.org.apache.bcel.internal.generic.RETURN;
import org.springframework.stereotype.Component;

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
@Component
public class JavaDockerCodeSandBox extends CodeSandBoxTemplate {
    private static Boolean FIRST_PULL = true;

    private final static String IMAGE = "openjdk:8-alpine";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

    /**
     * 3.重写模板中的执行编译代码方法，在容器中执行
     *
     * @param userCodeFile
     * @param inputCaseList
     * @return
     */
    @Override
    public List<ExecuteMessage> runCompileFile(File userCodeFile, List<String> inputCaseList) {
        // 构建默认的DockerClient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 3.1 拉取Java运行环境镜像
        pullImage(dockerClient);

        // 3.2 创建docker容器，将编译后的文件上传到容器环境中
        String containerId = createContainerAndUploadFile(dockerClient, userCodeFile);

        // 3.3 启动容器,在容器中执行代码，获取输出结果
        List<ExecuteMessage> executeMessageList = getExecuteMessageList(dockerClient, containerId, inputCaseList);
        return executeMessageList;
    }

    /**
     * 3.1 拉取docker hub上现成的轻量级Java运行环境镜像
     *
     * @param dockerClient
     */
    public void pullImage(DockerClient dockerClient) {

        // 只拉取一次
        if (FIRST_PULL) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(IMAGE);
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
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
            FIRST_PULL = false;
        }
        System.out.println("拉取镜像完成");
    }

    /**
     * 3.2 创建docker容器，将编译后的文件上传到容器环境中
     *
     * @param dockerClient
     * @param userCodeFile
     * @return
     */
    public String createContainerAndUploadFile(DockerClient dockerClient, File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(IMAGE);
        // 配置容器主机参数
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L)
                .withCpuCount(1L);
        // 将代码目录挂载到容器的的/app目录
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        // 允许在容器运行时从外部向容器输入数据、从容器获取标准（错误）输出，启用tty伪终端，使容器支持交互式终端操作
        CreateContainerResponse containerResponse = createContainerCmd.withHostConfig(hostConfig)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec();
        System.out.println(containerResponse);
        return containerResponse.getId();
    }

    /**
     * @param dockerClient
     * @param containerId
     * @param inputCaseList
     * @return
     */
    public List<ExecuteMessage> getExecuteMessageList(DockerClient dockerClient, String containerId,
                                                      List<String> inputCaseList) {
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        // docker exec keen_blackwell java -cp /app Main 1 3
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
        return executeMessageList;
    }
}

