package com.dusk.module.workflow.controller;

import com.dusk.common.core.controller.CruxBaseController;
import com.dusk.common.core.exception.BusinessException;
import com.dusk.common.core.tenant.TenantContextHolder;
import com.dusk.module.workflow.dto.GetRelateNodeInput;
import com.dusk.module.workflow.dto.GetRelateTaskInput;
import com.dusk.module.workflow.dto.RelatedNodeInfo;
import com.dusk.module.workflow.service.IWorkflowService;
import com.dusk.workflow.dto.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.image.ProcessDiagramGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
@Tag(description = "工作流管理", name = "Workflow")
public class WorkflowController extends CruxBaseController {
    @Autowired
    private IWorkflowService workflowService;
    @Autowired(required = false)
    private RepositoryService repositoryService;
    @Autowired
    private ProcessEngineConfiguration processEngineConfiguration;


    @SneakyThrows
    @Schema(description = "根据流程id获取流程图")
    @GetMapping(value = "/resource/{processId}")
    public void resourceRead(@PathVariable("processId") String processId, HttpServletResponse response) {
        byte[] data = workflowService.readResource(processId);
        response.setContentType("image/png");
        ServletOutputStream os = response.getOutputStream();
        os.write(data);
        os.flush();
        os.close();
    }

    @Schema(description = "获取流程历史记录")
    @GetMapping(value = "/getTaskHistory/{processId}")
    public List<WorkflowTaskHistoryDto> getTaskHistory(@PathVariable("processId") String processId) {
        return workflowService.getTaskHistory(processId);
    }

    @Schema(description = "获取流程历史记录（多个流程id）")
    @GetMapping(value = "/getTaskHistories")
    public List<WorkflowTaskHistoryDto> getTaskHistories(@RequestParam(value = "processInstanceId") String[] processInstanceIds) {
        return workflowService.getTaskHistories(Arrays.asList(processInstanceIds));
    }

    @Schema(description = "判断当前流程当前是否允许撤回")
    @GetMapping(value = "/checkProcessCanRecallPre")
    public boolean checkProcessCanRecallPre(@RequestParam(value = "processInstanceId", required = true) String processInstanceId) {
        return workflowService.checkProcessCanRecallPre(processInstanceId);
    }


    @Schema(description = "撤回到上一节点")
    @GetMapping(value = "/recallPre")
    public void recallPre(@RequestParam(value = "processInstanceId", required = true) String processInstanceId) {
        workflowService.recallPre(processInstanceId);
    }


    @Schema(description = "根据流程得key获取流程图")
    @GetMapping(value = "/getWorkFlowImgByProcessKey/{processKey}")
    public void getWorkFlowImgByProcessKey(HttpServletResponse response, @PathVariable("processKey") String processKey) {
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery().processDefinitionTenantId(TenantContextHolder.getTenantId().toString()).processDefinitionKey(processKey).latestVersion().singleResult();
        if (pd == null) {
            throw new BusinessException("不存在名为" + processKey + "的流程或者尚未发布");
        }
        BpmnModel bm = repositoryService.getBpmnModel(pd.getId());
        ProcessDiagramGenerator diagramGenerator = processEngineConfiguration.getProcessDiagramGenerator();
        InputStream is = diagramGenerator.generateDiagram(bm, "png",
                processEngineConfiguration.getActivityFontName(),
                processEngineConfiguration.getLabelFontName(), processEngineConfiguration.getAnnotationFontName(),
                processEngineConfiguration.getClassLoader(), true);
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


    @Schema(description = "根据运行实例,获取当前所有任务")
    @GetMapping("/getTasksByProcess")
    public List<WorkflowTaskDto> getTasksByProcess(@RequestParam("processInstanceId") String[] processInstanceIds) {
        return workflowService.getTasksByProcess(Arrays.asList(processInstanceIds));
    }

    @Schema(description = "根据运行实例,获取当前所有任务(不过滤权限，任何人都可以看到)")
    @GetMapping("/getTasksByProcessWithoutAuth")
    public List<WorkflowTaskDto> getTasksByProcessWithoutAuth(@RequestParam("processInstanceId") String[] processInstanceIds) {
        return workflowService.getTasksByProcess(Arrays.asList(processInstanceIds), false);
    }

    @Schema(description = "根据运行实例,获取关联的节点任务")
    @PostMapping("/getRelateTask")
    public List<WorkflowTaskDto> getRelateTask(@RequestBody GetRelateTaskInput input) {
        return workflowService.getRelateTask(input.getTaskId(), input.isAutoCalculate(), input.getVariables());
    }


    @Schema(description = "根据运行实例或者流程key,获取关联的节点信息")
    @PostMapping("/getRelateNode")
    public List<RelatedNodeInfo> getRelateNode(@RequestBody GetRelateNodeInput input) {
        return workflowService.getRelatedNode(input.getTaskId(), input.getProcessKey(), input.isAutoCalculate(), input.getVariables());
    }

    @Schema(description = "查询流程定义里第一个节点得formkey(startEvent的formKey)")
    @PostMapping("/getProcessDefinitionFirstFormKey")
    public String getProcessDefinitionFirstFormKey(@RequestParam String processKey) {
        return workflowService.getProcessDefinitionFirstFormKey(processKey);
    }

    @Schema(description = "根据运行实例,获取当前任务包含待处理人的信息(不过滤权限，任何人都可以看到)")
    @GetMapping(value = "/getCurrTasksWithAssigneeInfos/{processId}")
    public List<WorkflowTaskDetailDto> getCurrTasksWithAssigneeInfos(@PathVariable("processId") String processId) {
        return workflowService.getCurrTasksWithAssigneeInfos(processId);
    }

    /**
     * 通用流程提交（带前置/后置处理器）
     * <p>
     * 执行流程：preSubmit → startProcess/completeFirst → postSubmit → 抄送
     * 处理器通过 processDefinitionKey 自动匹配 IWorkflowSubmitProcessor 实现
     * </p>
     */
    @Schema(description = "通用流程提交（带前置/后置处理器）")
    @PostMapping("/genericSubmit")
    public StartProcessOutDto genericSubmit(@RequestBody GenericSubmitInput input) {
        return workflowService.genericSubmit(input);
    }

    /**
     * 通用流程审批（带前置/后置处理器）
     * <p>
     * 执行流程：preApproval → completeTask → postApproval → 抄送
     * 处理器通过 processDefinitionKey 自动匹配 IWorkflowApprovalProcessor 实现
     * </p>
     *
     * @return 审批后产生的新任务列表，空列表表示流程已结束
     */
    @Schema(description = "通用流程审批（带前置/后置处理器）")
    @PostMapping("/genericApproval")
    public List<WorkflowTaskDto> genericApproval(@RequestBody GenericApprovalInput input) {
        return workflowService.genericApproval(input);
    }

    /**
     * 撤回流程至上一节点
     * <p>
     * 撤回后会同步待办、调用 IWorkflowRecallHandler 业务回调、发布 PROCESS_RECALLED 事件
     * </p>
     */
    @Schema(description = "撤回流程至上一节点")
    @PostMapping("/recallProcess")
    public void recallProcess(@RequestBody RecallProcessInput input) {
        workflowService.recallProcess(input);
    }

    /**
     * 节点跳转 —— 将流程从当前节点直接跳转到目标任意节点
     * <p>
     * 管理级功能，不做审批权限校验。
     * 跳转完成后会同步待办并发布 TASK_JUMPED 事件。
     * </p>
     */
    @Schema(description = "节点跳转")
    @PostMapping("/jumpToNode")
    public void jumpToNode(@RequestBody JumpToNodeInput input) {
        workflowService.jumpToNode(input);
    }

    /**
     * 发送抄送通知 —— 通过站内信 INotificationRpcService 向指定用户发送通知
     */
    @Schema(description = "发送抄送通知")
    @PostMapping("/carbonCopy")
    public void sendCarbonCopy(@RequestBody CarbonCopyInput input) {
        workflowService.sendCarbonCopy(input);
    }
}
