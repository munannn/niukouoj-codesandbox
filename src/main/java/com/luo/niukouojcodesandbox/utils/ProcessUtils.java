package com.luo.niukouojcodesandbox.utils;

import com.luo.niukouojcodesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author 木南
 * @version 1.0
 * @Description 进程执行工具类
 */
public class ProcessUtils {

    /**
     * 传入一个执行进程，获取执行进行执行信息封装返回
     *
     * @param process
     * @return
     */
    public static ExecuteMessage getProcessExecuteMessage(Process process, String operation) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            // 等待程序执行，获取退出值
            int exitValue = process.waitFor();
            executeMessage.setExitValue(exitValue);
            // 判断退出值，0为正常退出，其他为错误退出
            if (exitValue == 0) {
                System.out.println(operation + "成功");
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行从进程中读取正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine);
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());
            } else {
                System.out.println(operation + "失败，退出值为：" + exitValue);
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                // 逐行从进程中读取正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine);
                }
                System.out.println("compileOutputStringBuilder:" + compileOutputStringBuilder);
                // 逐行从进程中读取错误输出
                StringBuilder errorCompileOutputStringBuilder = new StringBuilder();
                BufferedReader errorBufferedReader =
                        new BufferedReader(new InputStreamReader(process.getInputStream()));
                String errorCompileOutputLine;
                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    errorCompileOutputStringBuilder.append(errorCompileOutputLine);
                }
                executeMessage.setErrorMessage(errorCompileOutputStringBuilder.toString());
            }
            stopWatch.stop();
            long time = stopWatch.getLastTaskTimeMillis();
            executeMessage.setTime(time);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return executeMessage;
    }
}
