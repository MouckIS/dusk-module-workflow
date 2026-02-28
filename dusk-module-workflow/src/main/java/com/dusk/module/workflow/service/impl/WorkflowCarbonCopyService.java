package com.dusk.module.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.dusk.module.workflow.event.WorkflowEventPublisher;
import com.dusk.workflow.dto.CarbonCopyInput;
import com.dusk.workflow.enums.WorkflowEventType;
import com.dusk.workflow.service.INotificationRpcService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作流抄送服务
 * <p>
 * 通过站内信 {@link INotificationRpcService} 实现抄送通知
 * </p>
 *
 * @author kefuming
 * @date 2026-02-28
 */
@Slf4j
@Component
public class WorkflowCarbonCopyService {

    @DubboReference(check = false)
    private INotificationRpcService notificationRpcService;

    @Autowired
    private WorkflowEventPublisher eventPublisher;

    /**
     * 发送抄送通知
     *
     * @param input 抄送输入
     */
    public void sendCarbonCopy(CarbonCopyInput input) {
        if (input == null || input.getCcUserIds() == null || input.getCcUserIds().isEmpty()) {
            return;
        }
        try {
            notificationRpcService.sendNotification(
                    input.getCcUserIds(),
                    input.getTitle(),
                    input.getContent(),
                    input.getBusinessType(),
                    input.getBusinessKey()
            );
            log.info("抄送站内信已发送: processInstanceId={}, ccUsers={}", input.getProcessInstanceId(), input.getCcUserIds());
        } catch (Exception e) {
            log.error("抄送站内信发送失败: processInstanceId={}", input.getProcessInstanceId(), e);
        }

        // 发布抄送事件
        eventPublisher.publish(WorkflowEventType.TASK_CC,
                input.getProcessInstanceId(), null, input.getBusinessKey(),
                input.getTaskId(), null, null,
                String.join(",", input.getCcUserIds()),
                null, null, null);
    }

    /**
     * 根据逗号分隔的用户ID字符串发送抄送
     *
     * @param ccUserIds           逗号分隔的用户ID
     * @param processInstanceId   流程实例ID
     * @param processDefinitionKey 流程定义Key
     * @param businessKey         业务主键
     * @param taskId              任务ID
     * @param title               标题
     * @param content             内容
     */
    public void sendCarbonCopy(String ccUserIds, String processInstanceId, String processDefinitionKey,
                               String businessKey, String taskId, String title, String content) {
        if (StrUtil.isBlank(ccUserIds)) {
            return;
        }
        List<String> userIdList = Arrays.stream(ccUserIds.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        if (userIdList.isEmpty()) {
            return;
        }
        CarbonCopyInput input = new CarbonCopyInput();
        input.setProcessInstanceId(processInstanceId);
        input.setTaskId(taskId);
        input.setCcUserIds(userIdList);
        input.setTitle(title);
        input.setContent(content);
        input.setBusinessKey(businessKey);
        input.setBusinessType(processDefinitionKey);
        sendCarbonCopy(input);
    }
}

