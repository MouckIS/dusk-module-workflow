package com.dusk.module.workflow.dto;

import com.dusk.workflow.enums.AssigneeTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link com.dusk.workflow.dto.WorkflowTaskDto} 中的派生的审批人类型分支测试。
 */
class WorkflowTaskDtoTest {

    private static com.dusk.workflow.dto.WorkflowTaskDto dtoWithAssignee(String assignee) {
        com.dusk.workflow.dto.WorkflowTaskDto dto = new com.dusk.workflow.dto.WorkflowTaskDto();
        dto.setAssignee(assignee);
        return dto;
    }

    @Test
    @DisplayName("getAssigneeType：空审批人 -> Role")
    void emptyAssignee() {
        assertThat(dtoWithAssignee(null).getAssigneeType()).isEqualTo(AssigneeTypeEnum.Role);
        assertThat(dtoWithAssignee("").getAssigneeType()).isEqualTo(AssigneeTypeEnum.Role);
    }

    @Test
    @DisplayName("getAssigneeType：首个值为数字 -> UserId")
    void numericAssignee() {
        assertThat(dtoWithAssignee("1001").getAssigneeType()).isEqualTo(AssigneeTypeEnum.UserId);
    }

    @Test
    @DisplayName("getAssigneeType：首个值非数字 -> Role")
    void nonNumericAssignee() {
        assertThat(dtoWithAssignee("roleA").getAssigneeType()).isEqualTo(AssigneeTypeEnum.Role);
    }

    @Test
    @DisplayName("getAssigneeType：多个审批人时只认第一个")
    void multiAssignee() {
        assertThat(dtoWithAssignee("1001,roleA").getAssigneeType()).isEqualTo(AssigneeTypeEnum.UserId);
        assertThat(dtoWithAssignee("roleA,1001").getAssigneeType()).isEqualTo(AssigneeTypeEnum.Role);
    }
}
