package com.dusk.workflow.service;

import java.util.List;

/**
 * 站内信通知RPC接口
 * <p>
 * 用于工作流抄送等场景下发送站内消息。
 * 由通知模块提供具体实现。
 * </p>
 *
 * @author kefuming
 * @date 2026-02-28
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

