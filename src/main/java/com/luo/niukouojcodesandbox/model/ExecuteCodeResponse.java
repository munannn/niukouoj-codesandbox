package com.luo.niukouojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author 木南
 * @version 1.0
 * @Description TODO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeResponse {
    /**
     * 接口信息
     */
    private String message;

    /**
     * 执行状态
     */
    private Integer status;

    /**
     * 判题信息
     */
    private JudgeInfo judgeInfo;

    /**
     * 输出用例
     */
    private List<String> outputCaseList;
}
