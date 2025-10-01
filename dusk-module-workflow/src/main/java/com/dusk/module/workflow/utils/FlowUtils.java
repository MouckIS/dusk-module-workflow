package com.dusk.module.workflow.utils;


import cn.hutool.core.util.StrUtil;
import com.dusk.workflow.IProcessDesHolder;
import com.dusk.workflow.dto.ProcessDesOutPutDto;
import com.dusk.workflow.dto.WorkflowTaskDto;
import com.dusk.workflow.service.IWorkFlowRpcService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author kefuming
 * @date 2021-01-12 10:25
 */
@Component
public class FlowUtils {
    @Reference
    private IWorkFlowRpcService workFlowRpcService;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 处理相关的内容
     *
     * @param processDesList
     */
    public void setProcessDes(List<? extends IProcessDesHolder> processDesList) {
        List<String> processIds = processDesList.stream().map(IProcessDesHolder::getProcessInstanceId).filter(StrUtil::isNotBlank).collect(Collectors.toList());
        if (processIds.isEmpty()) {
            return;
        }
        List<ProcessDesOutPutDto> processDescriptionList = workFlowRpcService.getProcessDescription(processIds);
        for (ProcessDesOutPutDto processDesOutPutDto : processDescriptionList) {
            for (IProcessDesHolder iProcessDesHolder : processDesList) {
                if (processDesOutPutDto.getProcessInstanceId().equals(iProcessDesHolder.getProcessInstanceId())) {
                    iProcessDesHolder.setProcessDes(processDesOutPutDto);
                    break;
                }
            }
        }
    }

    public void setProcessDes(IProcessDesHolder processDesHolder) {
        setProcessDes(new ArrayList<>() {{
            add(processDesHolder);
        }});
    }

    @SneakyThrows
    public <T> T getFormKey(String formKeyStr, Class<T> formKeyClass) {
        if (StrUtil.isBlank(formKeyStr)) {
            formKeyStr = "{}";
        }
        return objectMapper.readValue(formKeyStr, formKeyClass);
    }

    /**
     * 获取下一个任务节点的formKey
     *
     * @param taskId
     * @param variables
     * @param pass
     * @param formKeyClass
     * @param <T>
     * @return
     */
    @SneakyThrows
    @Deprecated
    public <T> T getNextTaskFormKey(String taskId, boolean pass, Map<String, Object> variables, Class<T> formKeyClass) {
        List<WorkflowTaskDto> list = workFlowRpcService.getRelateTask(taskId, true, variables);
        for (WorkflowTaskDto workflowTaskDto : list) {
            if (isMatchTask(pass, workflowTaskDto.getTaskDirection())) {
                return getFormKey(workflowTaskDto.getFormKey(), formKeyClass);
            }
        }
        return null;
    }

    public <T> T getNextTaskFormKey(String taskId, Map<String, Object> variables, Class<T> formKeyClass) {
        boolean pass = variables == null || (boolean) (variables.getOrDefault("pass", true));
        return getNextTaskFormKey(taskId, pass, variables, formKeyClass);
    }

    private boolean isMatchTask(boolean pass, String taskDirection) {
        return (pass && "to".equalsIgnoreCase(taskDirection)) || (!pass && "from".equalsIgnoreCase(taskDirection));
    }

}
