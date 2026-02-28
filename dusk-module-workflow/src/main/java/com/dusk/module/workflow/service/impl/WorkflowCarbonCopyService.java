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
 * 通过站内信 {@link INotificationRpcService} 向指定用户发送抄送通知，
 * 同时发布 {@code TASK_CC} 事件到MQ。
 * </p>
 * <p>
 * 抄送触发场景：
 * <ul>
 *   <li>通用提交接口 {@code genericSubmit()} 设置了 ccUserIds</li>
 *   <li>通用审批接口 {@code genericApproval()} 设置了 ccUserIds</li>
 *   <li>手动调用 REST {@code POST /workflow/carbonCopy} 或 RPC {@code sendCarbonCopy()}</li>
 * </ul>
 * </p>
 * <p>
 * {@code INotificationRpcService} 通过 Dubbo RPC 调用通知模块实现。
 * 如果通知模块不可用（{@code check=false}），发送失败仅记录日志，不阻断主流程。
 * </p>
 *
 * @author kefuming
 * @see INotificationRpcService
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

