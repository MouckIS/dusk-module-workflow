package com.dusk.workflow.dto;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.dusk.workflow.enums.AssigneeTypeEnum;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author kefuming
 * @date 2020-07-22 14:11
 */
@Getter
@Setter
public class WorkflowTaskDto implements Serializable {
    private String id;
    private String processInstanceId;
    private String processDefinitionId;
    private String executionId;
    private String assignee;
    private String owner;
    private String name;
    private String description;
    private String definitionKey;
    private Date createTime;
    private List<WorkflowIdentityLinkDto> identityLinks;
    private String taskDirection;
    private List<FormPropertyDto> formProperties;
    private String formKey;
    /**
     * 流程变量
     */
    private Map<String, Object> processVariables;
    //审批人员类型
    private AssigneeTypeEnum assigneeType;

    public AssigneeTypeEnum getAssigneeType() {
        //只认第一个切割,的字符 是 长整形的 就是 基于 用户审批，不支持 用户和角色混合的模式
        if (StrUtil.isNotEmpty(assignee) && NumberUtil.isLong(StrUtil.split(assignee, ",")[0])) {
            return AssigneeTypeEnum.UserId;
        } else {
            return AssigneeTypeEnum.Role;
        }
    }
}
