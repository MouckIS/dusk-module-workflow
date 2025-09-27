package com.dusk.module.workflow.dto;

import lombok.Getter;
import lombok.Setter;
import com.dusk.common.core.pusher.Navigation;
import com.dusk.common.core.pusher.NoticationLevel;
import com.dusk.common.core.pusher.PushType;

/**
 * @author kefuming
 * @date 2021-04-26 10:33
 */
@Getter
@Setter
public class AppPushDto {
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
}
