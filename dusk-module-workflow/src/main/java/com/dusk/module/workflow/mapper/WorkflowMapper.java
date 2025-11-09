package com.dusk.module.workflow.mapper;

import com.dusk.module.workflow.dto.AppPushDto;
import com.dusk.module.workflow.dto.ModelDto;
import com.dusk.module.workflow.dto.ProcessDefDto;
import com.dusk.workflow.dto.*;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ProcessDefinition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @author : kefuming
 * @date : 2025/11/9 13:23
 */
@Mapper
public interface WorkflowMapper {
    WorkflowMapper INSTANCE = org.mapstruct.factory.Mappers.getMapper(WorkflowMapper.class);

    ModelDto toDto(Model model);

    AppPushDto workflowProcessDtoToAppPushDto(WorkflowProcessDto dto);

    AppPushDto completeTaskByProcessIdInputDtoToAppPushDto(CompleteTaskByProcessIdInputDto dto);

    AppPushDto updateTaskAssigneeInputToAppPushDto(UpdateTaskAssigneeInput dto);

    AppPushDto completeTaskInputDtoToAppPushDto(CompleteTaskInputDto dto);

    AppPushDto updateFlowVariablesInputToAppPushDto(UpdateFlowVariablesInput dto);

    WorkflowTaskDetailDto workflowTaskDtoToWorkflowTaskDetailDto(WorkflowTaskDto dto);

    WorkflowProcessDto inputDtoToWorkflowProcessDto(StartProcessInputDto dto);

    CompleteTaskByProcessIdInputDto inputDtoToCompleteTaskByProcessIdInputDto(StartProcessInputDto dto);

    FormPropertyDto ToFormPropertyDto(FormProperty dto);

    @Mapping(source = "version", target = "revision")
    @Mapping(source = "resourceName", target = "xmlName")
    @Mapping(source = "diagramResourceName", target = "picName")
    ProcessDefDto toProcessDefDto(ProcessDefinition processDefinition);
}
