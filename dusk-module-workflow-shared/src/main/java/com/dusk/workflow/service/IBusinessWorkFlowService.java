package com.dusk.workflow.service;


import com.dusk.common.core.dto.SelectableApproverDto;
import com.dusk.workflow.dto.WorkflowGetApproverInput;

import java.util.Collections;
import java.util.List;

/**
 * 业务工作流接口定义
 *
 * @author kefuming
 * @date 2021-08-05 15:25
 */
public interface IBusinessWorkFlowService {
    /**
     * 获取第一个流程节点formKey
     *
     * @return
     */
    String getProcessDefinitionFirstFormKey();

    /**
     * 获取可选的审批人列表
     *
     * @param input
     * @return
     */
    default List<SelectableApproverDto> getWorkflowApprovers(WorkflowGetApproverInput input) {
        return Collections.emptyList();
    }

    String getProcessKey();
}

