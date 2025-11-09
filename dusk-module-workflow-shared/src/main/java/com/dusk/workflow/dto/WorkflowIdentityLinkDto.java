package com.dusk.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author kefuming
 * @date 2020-07-22 14:12
 */
@Getter
@Setter
public class WorkflowIdentityLinkDto implements Serializable {
    private String type;
    private String userId;
    private String id;
    private String groupId;
    private String taskId;
    private String processInstanceId;
    private String processDefId;
}
