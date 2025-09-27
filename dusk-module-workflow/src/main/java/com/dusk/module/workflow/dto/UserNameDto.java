package com.dusk.module.workflow.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import com.dusk.common.core.annotation.UserName;

/**
 * @author kefuming
 * @date 2020-12-08 11:54
 */
@Getter
@Setter
@FieldNameConstants
public class UserNameDto {
    private String processInstanceId;
    private Long assigneeId;
    @UserName(UserNameDto.Fields.assigneeId)
    private String assigneeName;
}
