package com.dusk.workflow.service;

import java.util.List;

/**
 * 站内信通知RPC接口
 * <p>
 * 用于工作流抄送等场景下发送站内消息通知。
 * 由通知模块（如 dusk-module-notification）提供 Dubbo 实现。
 * 工作流模块中的 {@code WorkflowCarbonCopyService} 通过此接口发送抄送站内信。
 * </p>
 * <p>
 * 如果通知模块尚未实现此接口，工作流的抄送功能会降级处理（记录错误日志但不阻断主流程）。
 * </p>
 *
 * @author kefuming
 */
public interface INotificationRpcService {

    /**
     * 发送站内信
     *
     * @param userIds      接收人用户ID列表
     * @param title        消息标题
     * @param content      消息内容
     * @param businessType 业务类型
     * @param businessKey  业务主键
     */
    void sendNotification(List<String> userIds, String title, String content, String businessType, String businessKey);
}

