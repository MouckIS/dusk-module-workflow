package com.dusk.module.workflow.service.impl;

import cn.hutool.core.io.IoUtil;
import lombok.extern.slf4j.Slf4j;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.activiti.image.ProcessDiagramGenerator;
import org.activiti.spring.ProcessEngineFactoryBean;
import com.dusk.common.framework.dto.PagedAndSortedInputDto;
import com.dusk.common.framework.dto.PagedResultDto;
import com.dusk.common.framework.tenant.TenantContextHolder;
import com.dusk.module.workflow.service.IActTaskService;
import com.dusk.common.module.activiti.dto.WorkflowTaskDto;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author kefuming
 * @date 2020-07-22 14:19
 */
@Service
@Transactional
@Slf4j
public class ActTaskServiceImpl implements IActTaskService {

    @Autowired
    TaskService taskService;
    @Autowired
    RuntimeService runtimeService;
    @Autowired
    RepositoryService repositoryService;
    @Autowired
    HistoryService historyService;
    @Autowired
    ProcessEngineFactoryBean processEngine;

    @Override
    public PagedResultDto<WorkflowTaskDto> getTasks(PagedAndSortedInputDto input) {
        TaskQuery taskQuery = taskService.createTaskQuery().taskTenantId(String.valueOf(TenantContextHolder.getTenantId()));
        long total=taskQuery.count();
        List<WorkflowTaskDto> result = taskQuery.listPage((input.getPageNumber() - 1) * input.getPageSize(), input.getPageSize()).stream().map(task -> {
            WorkflowTaskDto workflowTaskDto = new WorkflowTaskDto();
            BeanUtils.copyProperties(task, workflowTaskDto, "identityLinks");
            return workflowTaskDto;
        }).collect(Collectors.toList());
        return new PagedResultDto<>(total,result);
    }

    @Override
    public byte[] viewByTaskId(String id) {
        //使用当前任务ID，获取当前任务对象
        Task task = taskService.createTaskQuery()
                .taskId(id)
                .singleResult();
        String processInstanceId = task.getProcessInstanceId();
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        HistoricProcessInstance historicProcessInstance =
                historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstanceId).singleResult();
        String processDefinitionId = null;
        List<String> executedActivityIdList = new ArrayList<>();
        if (processInstance != null) {
            processDefinitionId = processInstance.getProcessDefinitionId();
            executedActivityIdList = this.runtimeService.getActiveActivityIds(processInstance.getId());
        } else if (historicProcessInstance != null) {
            processDefinitionId = historicProcessInstance.getProcessDefinitionId();
            executedActivityIdList = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .orderByHistoricActivityInstanceId().asc().list()
                    .stream().map(HistoricActivityInstance::getActivityId)
                    .collect(Collectors.toList());
        }

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        ProcessEngineConfiguration processEngineConfiguration = processEngine.getProcessEngineConfiguration();
        Context.setProcessEngineConfiguration((ProcessEngineConfigurationImpl) processEngineConfiguration);
        ProcessDiagramGenerator diagramGenerator = processEngineConfiguration.getProcessDiagramGenerator();

        InputStream inputStream = diagramGenerator.generateDiagram(
                bpmnModel, "png",
                executedActivityIdList, Collections.emptyList(),
                processEngine.getProcessEngineConfiguration().getActivityFontName(),
                processEngine.getProcessEngineConfiguration().getLabelFontName(),
                "宋体",
                null, 1.0);
        return IoUtil.readBytes(inputStream);
    }
}
