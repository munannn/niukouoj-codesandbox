package com.luo.niukouojcodesandbox.model;

import lombok.Data;

/**
 * 判题信息
 *
 * @author 木南
 * @version 1.0
 * @Description TODO
 */
@Data
public class JudgeInfo {
    /**
     * 程序执行信息
     */
    private String message;

    /**
     * 消耗内存（KB）
     */
    private Long memory;

    /**
     * 消耗时间 (MS)
     */
    private Long time;

}
