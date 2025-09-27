package com.dusk.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * @author kefuming
 * @date 2021-12-27 14:39
 */
@Getter
@Setter
public class UpdateTaskAssigneeInput implements Serializable {
    /**
     * 任务id
     */
    private String taskId;

    private String assignee;


    //以下是顶部推送相关

    //以下是手机顶部推送配置
    /**
     * 是否自动推送手机顶部消息。默认不推送
     */
    private boolean autoAppPush = false;

    /**
     * 推送类型，默认是notice
     */
    private PushType pushType = PushType.NOTICE;

    /**
     * app推送标题，为空则用待办的类型
     */
    private String appTitle;

    /**
     * app推送正文，为空则用待办的title
     */
    private String appBody;

    /**
     * 推送级别 默认是info
     */
    private NoticationLevel noticationLevel = NoticationLevel.Info;

    /**
     * 顶部推送导航跳转参数
     */
    private Navigation navigation;

    /**
     * 业务数据
     */
    private Map<String, Object> businessData;
}
