package com.dusk.module.workflow.constant;

/**
 * @author kefuming
 * @date 2020-12-10 10:37
 */
public class ActivitiConstants {
    /**
     * 待办的类型 用于前端跳转判断用
     */
    public static final String TYPE = "activiti";

    /**
     * 用于显示待办的标题流转参数
     */
    public static final String TITLE = "activiTitle";
    /**
     * 用于显示待办的类型流转参数
     */
    public static final String TYPE_NAME = "activiTypeName";

    /**
     * 用于待办类型流转参数
     */
    public static final String BUSINESS_TYPE = "activiBusinessType";


    /**
     * 用于待办是否过滤场站标识
     */
    public static final String FILTER_STATION = "activiFilterStation";

    /**
     * 发起人
     */
    public static final String STARTER = "activiStarter";


    public static final String NODE_TYPE_START_EVENT = "StartEvent";

    public static final String NODE_TYPE_USER_TASK = "UserTask";

    public static final String NODE_TYPE_END_EVENT = "EndEvent";


    /**
     * 审批人占位符，直属上级
     */
    public static final String PLACE_HOLDER_DIRECT_LEADER = "{{directLeader}}";

}
