package com.dusk.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * @author kefuming
 * @date 2020-07-22 16:01
 */
@Getter
@Setter
@FieldNameConstants
public class WorkflowTaskHistoryDto implements Serializable {
    private String id;
    private String name;
    private String deleteReason;
    private String executionId;
    private String description;
    private String owner;
    @JsonIgnore
    private Long assigneeId;
    @UserName(Fields.assigneeId)
    private String assigneeName;
    private String formKey;
    private String tenantId;
    private String processInstanceId;
    private LocalDateTime beginTime;
    private LocalDateTime stopTime;
    private Long durationInMillis;
    private String comment;
    private Map<String, Object> variables;
}
