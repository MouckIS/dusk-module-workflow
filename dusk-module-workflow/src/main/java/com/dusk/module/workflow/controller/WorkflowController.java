package com.dusk.module.workflow.controller;

import com.dusk.common.core.controller.CruxBaseController;
import com.dusk.common.core.exception.BusinessException;
import com.dusk.common.core.tenant.TenantContextHolder;
import com.dusk.module.workflow.dto.GetRelateNodeInput;
import com.dusk.module.workflow.dto.GetRelateTaskInput;
import com.dusk.module.workflow.dto.RelatedNodeInfo;
import com.dusk.module.workflow.service.IWorkflowService;
import com.dusk.workflow.dto.WorkflowTaskDetailDto;
import com.dusk.workflow.dto.WorkflowTaskDto;
import com.dusk.workflow.dto.WorkflowTaskHistoryDto;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.image.ProcessDiagramGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * @author kefuming
 * @date 2020-11-16 16:23
 */
@Slf4j
@RestController
@RequestMapping("/workflow")
@Api(description = "工作流管理", tags = "Workflow")
public class WorkflowController extends CruxBaseController {
    @Autowired
    IWorkflowService workflowService;

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private ProcessEngineConfiguration processEngineConfiguration;


    @SneakyThrows
    @ApiOperation(value = "根据流程id获取流程图")
    @GetMapping(value = "/resource/{processId}")
    public void resourceRead(@PathVariable String processId, HttpServletResponse response) {
        byte[] data = workflowService.readResource(processId);
        response.setContentType("image/png");
        ServletOutputStream os = response.getOutputStream();
        os.write(data);
        os.flush();
        os.close();
    }

    @ApiOperation(value = "获取流程历史记录")
    @GetMapping(value = "/getTaskHistory/{processId}")
    public List<WorkflowTaskHistoryDto> getTaskHistory(@PathVariable String processId) {
        return workflowService.getTaskHistory(processId);
    }

    @ApiOperation(value = "获取流程历史记录（多个流程id）")
    @GetMapping(value = "/getTaskHistories")
    public List<WorkflowTaskHistoryDto> getTaskHistories(@RequestParam(value = "processInstanceId") String[] processInstanceIds) {
        return workflowService.getTaskHistories(Arrays.asList(processInstanceIds));
    }

    @ApiOperation(value = "判断当前流程当前是否允许撤回")
    @GetMapping(value = "/checkProcessCanRecallPre")
    public boolean checkProcessCanRecallPre(@RequestParam(value = "processInstanceId", required = true) String processInstanceId) {
        return workflowService.checkProcessCanRecallPre(processInstanceId);
    }


    @ApiOperation(value = "撤回到上一节点")
    @GetMapping(value = "/recallPre")
    public void recallPre(@RequestParam(value = "processInstanceId", required = true) String processInstanceId) {
        workflowService.recallPre(processInstanceId);
    }


    @ApiOperation(value = "根据流程得key获取流程图")
    @GetMapping(value = "/getWorkFlowImgByProcessKey/{processKey}")
    public void getWorkFlowImgByProcessKey(HttpServletResponse response, @PathVariable String processKey) {
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery().processDefinitionTenantId(TenantContextHolder.getTenantId().toString()).processDefinitionKey(processKey).latestVersion().singleResult();
        if (pd == null) {
            throw new BusinessException("不存在名为" + processKey + "的流程或者尚未发布");
        }
        BpmnModel bm = repositoryService.getBpmnModel(pd.getId());
        ProcessDiagramGenerator diagramGenerator = processEngineConfiguration.getProcessDiagramGenerator();
        InputStream is = diagramGenerator.generateDiagram(bm, "png",
                processEngineConfiguration.getActivityFontName(),
                processEngineConfiguration.getLabelFontName(), processEngineConfiguration.getAnnotationFontName(),
                processEngineConfiguration.getClassLoader(), 1.0);
        try {
            int size = is.available();
            byte[] data = new byte[size];
            is.read(data);
            response.setContentType("image/png");
            OutputStream os = response.getOutputStream();
            os.write(data);
            os.flush();
            os.close();
        } catch (Exception e) {
            log.error("获取流程图片异常", e);
        }
    }


    @ApiOperation(value = "根据运行实例,获取当前所有任务")
    @GetMapping("/getTasksByProcess")
    public List<WorkflowTaskDto> getTasksByProcess(@RequestParam("processInstanceId") String[] processInstanceIds) {
        return workflowService.getTasksByProcess(Arrays.asList(processInstanceIds));
    }

    @ApiOperation(value = "根据运行实例,获取当前所有任务(不过滤权限，任何人都可以看到)")
    @GetMapping("/getTasksByProcessWithoutAuth")
    public List<WorkflowTaskDto> getTasksByProcessWithoutAuth(@RequestParam("processInstanceId") String[] processInstanceIds) {
        return workflowService.getTasksByProcess(Arrays.asList(processInstanceIds), false);
    }

    @ApiOperation(value = "根据运行实例,获取关联的节点任务")
    @PostMapping("/getRelateTask")
    public List<WorkflowTaskDto> getRelateTask(@RequestBody GetRelateTaskInput input) {
        return workflowService.getRelateTask(input.getTaskId(), input.isAutoCalculate(), input.getVariables());
    }


    @ApiOperation(value = "根据运行实例或者流程key,获取关联的节点信息")
    @PostMapping("/getRelateNode")
    public List<RelatedNodeInfo> getRelateNode(@RequestBody GetRelateNodeInput input) {
        return workflowService.getRelatedNode(input.getTaskId(), input.getProcessKey(), input.isAutoCalculate(), input.getVariables());
    }

    @ApiOperation(value = "查询流程定义里第一个节点得formkey(startEvent的formKey)")
    @PostMapping("/getProcessDefinitionFirstFormKey")
    public String getProcessDefinitionFirstFormKey(@RequestParam String processKey) {
        return workflowService.getProcessDefinitionFirstFormKey(processKey);
    }

    @ApiOperation(value = "根据运行实例,获取当前任务包含待处理人的信息(不过滤权限，任何人都可以看到)")
    @GetMapping(value = "/getCurrTasksWithAssigneeInfos/{processId}")
    public List<WorkflowTaskDetailDto> getCurrTasksWithAssigneeInfos(@PathVariable String processId) {
        return workflowService.getCurrTasksWithAssigneeInfos(processId);
    }
}
