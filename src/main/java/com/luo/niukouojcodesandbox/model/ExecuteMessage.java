package com.luo.niukouojcodesandbox.model;

import lombok.Data;

/**
 * @author 木南
 * @version 1.0
 * @Description 执行信息
 */
@Data
public class ExecuteMessage {
    /**
     * 退出值，0为正常退出
     */
    private Integer exitValue;

    /**
     * 正常退出输出信息
     */
    private String message;

    /**
     * 异常退出输出信息
     */
    private String errorMessage;

    /**
     * 程序执行时间
     */
    private Long time;

    /**
     * 程序占用内存
     */
    private Long memory;
}
