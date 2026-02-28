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
 * 容器启动时自动收集所有 {@link IWorkflowSubmitProcessor} 和 {@link IWorkflowApprovalProcessor} 的 Spring Bean，
 * 按 {@code processKey} 建立索引映射。在 {@code WorkflowServiceImpl.genericSubmit()} 和 {@code genericApproval()}
 * 方法中，根据流程定义Key查找对应的处理器实例来执行前/后置逻辑。
 * </p>
 * <p>
 * 每个 processKey 仅允许注册一个处理器，重复注册时后者覆盖前者并输出警告日志。
 * 没有找到处理器时不影响流程正常执行（处理器是可选的扩展点）。
 * </p>
 *
 * @author kefuming
 * @see IWorkflowSubmitProcessor
 * @see IWorkflowApprovalProcessor
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


