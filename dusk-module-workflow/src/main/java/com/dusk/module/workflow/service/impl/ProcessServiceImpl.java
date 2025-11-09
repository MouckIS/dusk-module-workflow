package com.dusk.module.workflow.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.dusk.common.core.dto.PagedResultDto;
import com.dusk.common.core.tenant.TenantContextHolder;
import com.dusk.module.workflow.dto.GetProcessesInput;
import com.dusk.module.workflow.dto.ProcessDefDto;
import com.dusk.module.workflow.mapper.WorkflowMapper;
import com.dusk.module.workflow.service.IProcessService;
import lombok.extern.slf4j.Slf4j;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.activiti.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author kefuming
 * @date 2020-07-22 13:37
 */
@Transactional
@Slf4j
@Service
public class ProcessServiceImpl implements IProcessService {
    private final WorkflowMapper mapper = WorkflowMapper.INSTANCE;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RuntimeService runtimeService;

    @Override
    public boolean removeProcIns(String deploymentId) {
        repositoryService.deleteDeployment(deploymentId, true);
        return true;
    }

    @Override
    public PagedResultDto<ProcessDefDto> getProcesses(GetProcessesInput input) {
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery()
                .processDefinitionTenantId(String.valueOf(TenantContextHolder.getTenantId())).latestVersion();
        if (StrUtil.isNotBlank(input.getCategory())) {
            query.processDefinitionCategory(input.getCategory());
        }
        long total = query.count();

        List<ProcessDefinition> processDefinitionList;
        if (input.isUnPage()) {
            processDefinitionList = query.list();
        } else {
            processDefinitionList = query.listPage((input.getPageNumber() - 1) * input.getPageSize(), input.getPageSize());
        }

        List<ProcessDefDto> collect = processDefinitionList.stream().map(processDefinition -> {
            Deployment deployment = repositoryService.createDeploymentQuery()
                    .deploymentId(processDefinition.getDeploymentId()).singleResult();
            ProcessDefDto dto = mapper.toProcessDefDto(processDefinition);
            dto.setDeploymentId(deployment.getId());
            dto.setName(deployment.getName());
            dto.setDeploymentTime(deployment.getDeploymentTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            return dto;
        }).collect(Collectors.toList());

        return new PagedResultDto<>(total, collect);
    }

    @Override
    public byte[] getResource(String proInsId, String resType) {
        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(proInsId)
                .singleResult();
        var procDefId = processInstance.getProcessDefinitionId();
        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(procDefId)
                .singleResult();
        String resourceName = "";
        if ("image".equals(resType)) {
            resourceName = processDefinition.getDiagramResourceName();
        } else if ("xml".equals(resType)) {
            resourceName = processDefinition.getResourceName();
        }

        InputStream resourceAsStream = repositoryService
                .getResourceAsStream(processDefinition.getDeploymentId(), resourceName);
        return IoUtil.readBytes(resourceAsStream);
    }
}
