package com.dusk.workflow.dto;

import com.dusk.workflow.enums.AssigneeTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkflowTaskDto 单元测试
 * 覆盖getAssigneeType()全部分支：数字assignee→UserId、非数字→Role、空/null→Role
 */
class WorkflowTaskDtoTest {

    @Test
    void getAssigneeType_numericAssignee_shouldReturnUserId() {
        WorkflowTaskDto dto = new WorkflowTaskDto();
        dto.setAssignee("12345");
        assertEquals(AssigneeTypeEnum.UserId, dto.getAssigneeType());
    }

    @Test
    void getAssigneeType_numericCommaSeparated_shouldReturnUserId() {
        WorkflowTaskDto dto = new WorkflowTaskDto();
        dto.setAssignee("100,200,300");
        assertEquals(AssigneeTypeEnum.UserId, dto.getAssigneeType());
    }

    @Test
    void getAssigneeType_nonNumericAssignee_shouldReturnRole() {
        WorkflowTaskDto dto = new WorkflowTaskDto();
        dto.setAssignee("部门经理");
        assertEquals(AssigneeTypeEnum.Role, dto.getAssigneeType());
    }

    @Test
    void getAssigneeType_mixedAssigneeStartsWithRole_shouldReturnRole() {
        WorkflowTaskDto dto = new WorkflowTaskDto();
        dto.setAssignee("管理员,100");
        assertEquals(AssigneeTypeEnum.Role, dto.getAssigneeType());
    }

    @Test
    void getAssigneeType_emptyAssignee_shouldReturnRole() {
        WorkflowTaskDto dto = new WorkflowTaskDto();
        dto.setAssignee("");
        assertEquals(AssigneeTypeEnum.Role, dto.getAssigneeType());
    }

    @Test
    void getAssigneeType_nullAssignee_shouldReturnRole() {
        WorkflowTaskDto dto = new WorkflowTaskDto();
        dto.setAssignee(null);
        assertEquals(AssigneeTypeEnum.Role, dto.getAssigneeType());
    }
}

