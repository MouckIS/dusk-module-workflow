package com.dusk.module.workflow.service;

import com.dusk.workflow.service.IWorkflowApprovalProcessor;
import com.dusk.workflow.service.IWorkflowSubmitProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作流处理器注册中心
 * <p>
 * 收集所有 {@link IWorkflowSubmitProcessor} 和 {@link IWorkflowApprovalProcessor} 实现，
 * 并按 processKey 索引。
 * </p>
 *
 * @author kefuming
 * @date 2026-02-28
 */
@Slf4j
@Component
public class WorkflowProcessorRegistry {

    private final Map<String, IWorkflowSubmitProcessor> submitProcessors;
    private final Map<String, IWorkflowApprovalProcessor> approvalProcessors;

    @Autowired
    public WorkflowProcessorRegistry(
            @Autowired(required = false) List<IWorkflowSubmitProcessor> submitProcessorList,
            @Autowired(required = false) List<IWorkflowApprovalProcessor> approvalProcessorList) {
        this.submitProcessors = submitProcessorList == null ? Collections.emptyMap()
                : submitProcessorList.stream().collect(Collectors.toMap(
                IWorkflowSubmitProcessor::getProcessKey, Function.identity(),
                (a, b) -> {
                    log.warn("重复的SubmitProcessor: processKey={}", a.getProcessKey());
                    return b;
                }));
        this.approvalProcessors = approvalProcessorList == null ? Collections.emptyMap()
                : approvalProcessorList.stream().collect(Collectors.toMap(
                IWorkflowApprovalProcessor::getProcessKey, Function.identity(),
                (a, b) -> {
                    log.warn("重复的ApprovalProcessor: processKey={}", a.getProcessKey());
                    return b;
                }));
        log.info("已注册 {} 个SubmitProcessor, {} 个ApprovalProcessor",
                this.submitProcessors.size(), this.approvalProcessors.size());
    }

    /**
     * 获取提交处理器
     */
    public IWorkflowSubmitProcessor getSubmitProcessor(String processKey) {
        return submitProcessors.get(processKey);
    }

    /**
     * 获取审批处理器
     */
    public IWorkflowApprovalProcessor getApprovalProcessor(String processKey) {
        return approvalProcessors.get(processKey);
    }
}


