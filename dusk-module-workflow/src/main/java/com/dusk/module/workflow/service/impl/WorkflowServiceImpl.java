package com.dusk.module.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.dusk.common.core.auth.authentication.LoginUserIdContextHolder;
import com.dusk.common.core.exception.BusinessException;
import com.dusk.common.core.model.UserContext;
import com.dusk.common.core.tenant.TenantContextHolder;
import com.dusk.common.core.utils.DateUtils;
import com.dusk.common.core.utils.MapperUtil;
import com.dusk.common.core.utils.SecurityUtils;
import com.dusk.common.rpc.auth.UserNameUtils;
import com.dusk.common.rpc.auth.dto.ToDoDto;
import com.dusk.common.rpc.auth.dto.UserFullListDto;
import com.dusk.common.rpc.auth.enums.ToDoTargetType;
import com.dusk.common.rpc.auth.service.ITodoRpcService;
import com.dusk.common.rpc.auth.service.IUserRpcService;
import com.dusk.module.workflow.constant.FlowableConstants;
import com.dusk.module.workflow.dto.*;
import com.dusk.module.workflow.mapper.WorkflowMapper;
import com.dusk.module.workflow.service.IWorkflowService;
import com.dusk.workflow.dto.*;
import com.dusk.workflow.enums.AssigneeTypeEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
//import org.activiti.bpmn.model.BpmnModel;
//import org.activiti.engine.*;
//import org.activiti.engine.delegate.Expression;
//import org.activiti.engine.form.FormProperty;
//import org.activiti.engine.form.TaskFormData;
//import org.activiti.engine.history.HistoricActivityInstance;
//import org.activiti.engine.history.HistoricProcessInstance;
//import org.activiti.engine.history.HistoricTaskInstance;
//import org.activiti.engine.history.HistoricVariableInstance;
//import org.activiti.engine.impl.RepositoryServiceImpl;
//import org.activiti.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
//import org.activiti.engine.impl.form.DefaultStartFormHandler;
//import org.activiti.engine.impl.javax.el.ExpressionFactory;
//import org.activiti.engine.impl.javax.el.ValueExpression;
//import org.activiti.engine.impl.juel.ExpressionFactoryImpl;
//import org.activiti.engine.impl.juel.SimpleContext;
//import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
//import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
//import org.activiti.engine.impl.pvm.PvmActivity;
//import org.activiti.engine.impl.pvm.PvmTransition;
//import org.activiti.engine.impl.pvm.process.ActivityImpl;
//import org.activiti.engine.impl.pvm.process.TransitionImpl;
//import org.activiti.engine.impl.task.TaskDefinition;
//import org.activiti.engine.repository.ProcessDefinition;
//import org.activiti.engine.runtime.ProcessInstance;
//import org.activiti.engine.task.*;
//import org.activiti.image.ProcessDiagramGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.flowable.bpmn.model.*;
import org.flowable.common.engine.impl.de.odysseus.el.ExpressionFactoryImpl;
import org.flowable.common.engine.impl.de.odysseus.el.util.SimpleContext;
import org.flowable.common.engine.impl.javax.el.ExpressionFactory;
import org.flowable.common.engine.impl.javax.el.ValueExpression;
import org.flowable.engine.*;
import org.flowable.engine.form.FormProperty;
import org.flowable.engine.form.TaskFormData;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.RepositoryServiceImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskInfo;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * @author kefuming
 * @date 2020-07-22 16:15
 */
@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class WorkflowServiceImpl implements IWorkflowService {
    private static final String BOHUI = "驳回";
    private static final String CHEHUI = "撤回";
    private final WorkflowMapper mapper = WorkflowMapper.INSTANCE;
    @Autowired(required = false)
    private RuntimeService runtimeService;
    @Autowired(required = false)
    private TaskService taskService;
    @Autowired(required = false)
    private RepositoryService repositoryService;
    @Autowired(required = false)
    private ProcessEngineConfiguration processEngineConfiguration;
    @Autowired(required = false)
    private HistoryService historyService;
    @Autowired(required = false)
    private FormService formService;
    @DubboReference(timeout = 1500)
    private IUserRpcService userRpcService;
    @Resource
    private SecurityUtils securityUtils;
    @Resource
    private UserNameUtils userNameUtils;
    @DubboReference
    private ITodoRpcService todoRpcService;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public StartProcessOutDto startProcess(WorkflowProcessDto workflowProcessDto) {
        ProcessInstance processInstance = startNewProcess(workflowProcessDto);
        StartProcessOutDto result = new StartProcessOutDto();
        result.setProcessInstanceId(processInstance.getProcessInstanceId());
        List<String> params = new ArrayList<>();
        params.add(processInstance.getProcessInstanceId());
        List<WorkflowTaskDto> tasksByProcess = getTasksByProcessAndInitAssignee(params, false);
        result.setTaskInfos(tasksByProcess);
        //同步待办
        syncTodos(processInstance, tasksByProcess, mapper.workflowProcessDtoToAppPushDto(workflowProcessDto), workflowProcessDto.getBusinessData());
        //处理代办
        return result;
    }


    @Override
    @SneakyThrows
    public boolean delProcess(String processInstanceId, String deleteReason) {
        ProcessInstance processInstance =
                runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        if (processInstance != null) {
            runtimeService.deleteProcessInstance(processInstanceId, deleteReason);
            //同步待办
            syncTodos(processInstance, null, null, null);
        }
        return true;
    }

    @Override
    public boolean checkProcessEnd(String processInstanceId) {
        ProcessInstance processInstance =
                runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        return processInstance == null;
    }

    @Override
    public byte[] readResource(String processInstanceId) {
        HistoricProcessInstance processInstance =
                historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        String procDefId = processInstance.getProcessDefinitionId();

        // 当前活动节点、活动线
        List<String> activeActivityIds, highLightedFlows;

        // 获得历史活动记录实体
        List<HistoricActivityInstance> historicActivityInstances =
                historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId)
                        .orderByHistoricActivityInstanceStartTime().asc().list();

        // 获得当前活动的节点
        if (isFinished(processInstanceId)) {
            // 如果流程已经结束，则得到结束节点
             activeActivityIds = historicActivityInstances.stream().map(HistoricActivityInstance::getActivityId)
             .collect(Collectors.toList());

        } else {
            // 如果流程没有结束，则取当前活动节点
            // 根据流程实例ID获得当前处于活动状态的ActivityId合集
            activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
        }

        if (activeActivityIds.isEmpty()) {
            activeActivityIds
                    .add(historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId).activityType("endEvent").singleResult().getActivityId());
        }

        // 计算活动线 (暂时不显示活动路线)
        highLightedFlows = new ArrayList<>();
        // highLightedFlows = getHighLightedFlows(
        // 		(ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService)
        // 				.getDeployedProcessDefinition(procDefId),
        // 		historicActivityInstances);

        // 根据流程定义ID获得BpmnModel
        BpmnModel bpmnModel = repositoryService.getBpmnModel(procDefId);

        ProcessDiagramGenerator diagramGenerator = processEngineConfiguration.getProcessDiagramGenerator();
        String activityFontName = processEngineConfiguration.getActivityFontName();
        String labelFontName = processEngineConfiguration.getLabelFontName();
        String annotationFontName = processEngineConfiguration.getAnnotationFontName();
        InputStream is = diagramGenerator.generateDiagram(bpmnModel, "png", activeActivityIds, highLightedFlows,
                activityFontName, labelFontName, annotationFontName,
                processEngineConfiguration.getClassLoader(), 1.0, true);
        return IoUtil.readBytes(is);
    }

    @Override
    public WorkflowTaskDto getTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException("审批任务不存在");
        }
        return task2TaskDto(task, false);
    }

    @Override
    public List<ProcessDesOutPutDto> getProcessDescription(List<String> processIds) {
        List<ProcessDesOutPutDto> data = new ArrayList<>();
        List<UserNameDto> all = new ArrayList<>();
        if (processIds != null && !processIds.isEmpty()) {
            UserContext currentUser = securityUtils.getCurrentUser();
            UserFullListDto userInfo = currentUser == null ? null : userRpcService.getUserFullById(currentUser.getId());
            List<Task> collect = new ArrayList<>(taskService.createTaskQuery().processInstanceIdIn(processIds).list());
            for (String processId : processIds) {
                ProcessDesOutPutDto dto = new ProcessDesOutPutDto();
                dto.setProcessInstanceId(processId);
                List<Task> relatedTask = collect.stream().filter(p -> p.getProcessInstanceId().equals(processId)).toList();
                if (!relatedTask.isEmpty()) {
                    Map<String, Object> variables = runtimeService.getVariables(processId);
                    dto.setVariables(variables);
                    List<String> assignees = relatedTask.stream().map(TaskInfo::getAssignee).filter(StrUtil::isNotEmpty).collect(toList());
                    assignees.forEach(p -> {
                        for (String assignee : p.split(",")) {//兼容处理指定多用户的情况
                            UserNameDto nameDto = new UserNameDto();
                            nameDto.setProcessInstanceId(processId);
                            if (NumberUtil.isLong(assignee)) {
                                nameDto.setAssigneeId(Long.valueOf(assignee));
                            } else {
                                nameDto.setAssigneeName(assignee);
                            }
                            all.add(nameDto);
                        }
                    });
                    relatedTask.forEach(p -> assignees.addAll(getFormKeyAssignee(p.getFormKey())));
                    dto.setHasPermission(userInfo != null && hasTaskPermission(assignees, userInfo));
                    String taskName = ArrayUtil.join(relatedTask.stream().map(TaskInfo::getName).distinct().toList().toArray(), "/");
                    dto.setTaskName(taskName);
                    Task task = relatedTask.getFirst();//默认只有一个节点
                    dto.setFormKey(task.getFormKey());
                } else {
                    dto.setFinished(true);
                }
                data.add(dto);
            }

            List<UserNameDto> userNameDtos = userNameUtils.mapList(all, UserNameDto.class);
            for (ProcessDesOutPutDto item : data) {
                if (!item.isFinished()) {
                    List<String> temp = userNameDtos.stream().filter(p -> p.getProcessInstanceId().equals(item.getProcessInstanceId())).map(UserNameDto::getAssigneeName)
                            .toList();
                    if (!temp.isEmpty()) {
                        item.setDescription(StrUtil.format("待【{}】{}", ArrayUtil.join(temp.stream().distinct().toArray(), "，"), item.getTaskName()));
                    } else {
                        item.setDescription(item.getTaskName());
                    }
                } else {
                    item.setDescription("流程已结束");
                }
            }
        }


        return data;
    }

    @Override
    public List<WorkflowTaskDto> getTasksByProcess(List<String> processInstanceIds) {
        return getTasksByProcess(processInstanceIds, true);
    }

    @Override
    public String getProcessDefinitionFirstFormKey(String processKey) {
        ProcessDefinition pd =
                repositoryService.createProcessDefinitionQuery().processDefinitionTenantId(TenantContextHolder.getTenantId().toString())
                        .processDefinitionKey(processKey).latestVersion().singleResult();

        if (pd == null) {
            throw new BusinessException("不存在名为" + processKey + "的流程或者尚未发布");
        }
        ProcessDefinitionEntity processDefinition =
                (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(pd.getId());

        if (processDefinition == null) {
            throw new BusinessException("流程尚未发布");
        }

        if (processDefinition.getHasStartFormKey()) {
            //return ((DefaultStartFormHandler) processDefinition.getStartFormHandler()).getFormKey().getExpressionText();
            formService.getStartFormKey(processDefinition.getId());
        }
        return null;
    }

    @Override
    public boolean completeTaskByProcessId(CompleteTaskByProcessIdInputDto input) {
        ProcessInstance processInstance =
                runtimeService.createProcessInstanceQuery().processInstanceId(input.getProcessInstanceId()).singleResult();
        if (processInstance == null) {
            throw new BusinessException("无法找到流程");
        }
        String taskId = getTaskIdByProcessInstanceId(input.getProcessInstanceId());
        boolean result = completeTask(taskId, input, false);
        //同步待办
        List<String> params = new ArrayList<>();
        params.add(input.getProcessInstanceId());
        syncTodos(processInstance, getTasksByProcessAndInitAssignee(params, false), mapper.completeTaskByProcessIdInputDtoToAppPushDto(input), input.getBusinessData());
        return result;
    }

    @Override
    public boolean completeTask(String taskId, WorkflowCompleteTaskDto workflowCompleteTaskDto) {
        return completeTask(taskId, workflowCompleteTaskDto, true);
    }

    private boolean completeTask(String taskId, WorkflowCompleteTaskDto workflowCompleteTaskDto, boolean needAuth) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        String comment = workflowCompleteTaskDto.getComment();
        Map<String, Object> variables = workflowCompleteTaskDto.getVariables();
        Map<String, Object> localVariables = workflowCompleteTaskDto.getLocalVariables();
        Map<String, Object> transientVariables = workflowCompleteTaskDto.getTransientVariables();
        // 添加评论
        if (!StrUtil.isBlank(comment)) {
            taskService.addComment(taskId, task.getProcessInstanceId(), comment);
        }


        // 代理人
        if (!StrUtil.isBlank(task.getAssignee())) {
            if (needAuth) {
                boolean hasPermission = hasTaskPermission(task);
                if (!hasPermission) {
                    throw new BusinessException("没有权限，非法提交！");
                }
            }
        }
        //        taskService.setAssignee(taskId, securityUtils.getCurrentUser().getId().toString());
        //modify by pengjian 支持不登陆执行
        taskService.setAssignee(taskId, LoginUserIdContextHolder.getUserId() == null ? "" :
                LoginUserIdContextHolder.getUserId().toString());
        if (variables != null && !variables.isEmpty()) {
            // 全局变量
            taskService.setVariables(taskId, variables);
        }
        if (localVariables != null && !localVariables.isEmpty()) {
            // 局部变量
            taskService.setVariablesLocal(taskId, localVariables);
        }
        // 完成任务
        taskService.complete(taskId, variables, transientVariables);
        return true;
    }

    @Override
    public List<WorkflowTaskDto> getTaskList(List<String> processInstanceIds) {
        List<Task> list = taskService.createTaskQuery().processInstanceIdIn(processInstanceIds).list();
        List<WorkflowTaskDto> workflowTaskDtoList = new ArrayList<>();
        for (Task task : list) {
            WorkflowTaskDto workflowTaskDto = new WorkflowTaskDto();
            BeanUtils.copyProperties(task, workflowTaskDto, "identityLinks");
            workflowTaskDtoList.add(workflowTaskDto);
        }
        return workflowTaskDtoList;
    }

    @Override
    public void updateTaskAssignee(UpdateTaskAssigneeInput input) {
        Task task = taskService.createTaskQuery().taskId(input.getTaskId()).singleResult();
        if (task == null) {
            throw new BusinessException("任务不存在！");
        }
        task.setAssignee(input.getAssignee());
        taskService.saveTask(task);
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();
        syncTodosAssigneeChanged(processInstance, toTaskDto(Collections.singletonList(task), false), mapper.updateTaskAssigneeInputToAppPushDto(input), input.getBusinessData());
    }

    @Override
    public WorkflowTaskDto getNextTaskByProcessId(String processInstanceId, Map<String, Object> variables) {
        String taskId = getTaskIdByProcessInstanceId(processInstanceId);
        return getNextTask(taskId, variables);
    }

    @Override
    public WorkflowTaskDto getNextTask(String taskId, Map<String, Object> variables) {
        UserTask task = null;

        Task taskInstance = taskService.createTaskQuery().taskId(taskId).singleResult();
        ProcessInstance processInstance =
                runtimeService.createProcessInstanceQuery().processInstanceId(taskInstance.getProcessInstanceId()).singleResult();

        //获取流程发布Id信息
        String definitionId = processInstance.getProcessDefinitionId();
        Map<String, Object> processVariables = new HashMap<>();

        try {
            processVariables = runtimeService.getVariables(taskInstance.getExecutionId());
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        ProcessDefinitionEntity processDefinitionEntity =
                (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(definitionId);

        ExecutionEntity execution = (ExecutionEntity) processInstance;

        //当前流程节点Id信息
        String activitiId = execution.getActivityId();

        ////获取流程所有节点信息
        //List<ActivityImpl> activitiList = processDefinitionEntity.getActivities();
        //
        ////遍历所有节点信息
        //for (ActivityImpl activityImpl : activitiList) {
        //    String id = activityImpl.getId();
        //    if (activitiId.equals(id)) {
        //        //获取下一个节点信息
        //        task = nextTaskDefinition(activityImpl, activityImpl.getId(), variables);
        //        break;
        //    }
        //}

        BpmnModel bpmnModel = repositoryService.getBpmnModel(definitionId);
        FlowElement flowElement = bpmnModel.getFlowElement(activitiId);
        if (flowElement instanceof FlowNode) {
            task = nextTaskDefinition(flowElement, activitiId, variables);
        }

        if (task != null) {
            return getWorkflowTaskDto(task, processVariables);
        }

        return null;
    }

    @Override
    public List<WorkflowTaskDto> getRelateTask(String taskId, boolean autoCalculate, Map<String, Object> variables) {
        Task taskInstance = taskService.createTaskQuery().taskId(taskId).singleResult();
        String processInstanceId = taskInstance.getProcessInstanceId();
        List<WorkflowTaskDto> taskList = new ArrayList<>();

        ProcessInstance processInstance =
                runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();

        if (processInstance == null) {
            return taskList;
        }

        String excId = taskInstance.getExecutionId();
        ExecutionEntity execution =
                (ExecutionEntity) runtimeService.createExecutionQuery().executionId(excId).singleResult();

        //获取流程发布Id信息
        String definitionId = processInstance.getProcessDefinitionId();
        Map<String, Object> processVariables = runtimeService.getVariables(taskInstance.getExecutionId());

        if (variables != null) {
            processVariables.putAll(variables);
        }

        ProcessDefinitionEntity processDefinitionEntity =
                (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(definitionId);


        // 历史流转的节点
        List<HistoricActivityInstance> historicActivityInstanceList =
                historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId).finished()
                        .orderByHistoricActivityInstanceStartTime().asc().list();

        //当前流程节点Id信息
        String activityId = execution.getActivityId();

        //获取流程所有节点信息
        BpmnModel bpmnModel = repositoryService.getBpmnModel(execution.getProcessDefinitionId());
        FlowElement flowElement = bpmnModel.getFlowElement(activityId);
        if (flowElement instanceof UserTask userTask) {
            List<SequenceFlow> outgoingFlows = userTask.getOutgoingFlows();
            caculateLinkTask(outgoingFlows, taskList, processVariables, autoCalculate, null);
        }

        return taskList;
    }

    @Override
    public List<RelatedNodeInfo> getRelatedNode(String taskId, String processKey, boolean autoCalculate, Map<String, Object> variables) {
        List<RelatedNodeInfo> nodeList = new ArrayList<>();
        if (StrUtil.isBlank(taskId)) {

            if (StrUtil.isNotBlank(processKey)) {
                String processDefinitionFirstFormKey = getProcessDefinitionFirstFormKey(processKey);
                RelatedNodeInfo startEvent = new RelatedNodeInfo();
                startEvent.setFormKey(processDefinitionFirstFormKey);
                startEvent.setNodeType(FlowableConstants.NODE_TYPE_START_EVENT);
                nodeList.add(startEvent);
            } else {
                throw new BusinessException("taskId和processKey两者不能同时为空");
            }
        } else {
            Task taskInstance = taskService.createTaskQuery().taskId(taskId).singleResult();
            String processInstanceId = taskInstance.getProcessInstanceId();

            ProcessInstance processInstance =
                    runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();

            if (processInstance == null) {
                return nodeList;
            }

            String excId = taskInstance.getExecutionId();
            ExecutionEntity execution =
                    (ExecutionEntity) runtimeService.createExecutionQuery().executionId(excId).singleResult();

            //获取流程发布Id信息
            String definitionId = processInstance.getProcessDefinitionId();
            Map<String, Object> processVariables = runtimeService.getVariables(taskInstance.getExecutionId());

            if (variables != null) {
                processVariables.putAll(variables);
            }

            ProcessDefinitionEntity processDefinitionEntity =
                    (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(definitionId);


            // 历史流转的节点
            List<HistoricActivityInstance> historicActivityInstanceList =
                    historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId).finished()
                            .orderByHistoricActivityInstanceStartTime().asc().list();

            //当前流程节点Id信息
            String activityId = execution.getActivityId();

            // 1. 获取 BpmnModel (推荐缓存此对象或从 RepositoryService 获取)
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionEntity.getId());

            // 2. 直接根据 ID 获取节点，无需手动循环遍历所有 Activities
            FlowElement flowElement = bpmnModel.getFlowElement(activityId);

            // 3. 如果节点存在且属于流转节点 (FlowNode)
            if (flowElement instanceof FlowNode flowNode) {
                // 获取出线
                List<SequenceFlow> outgoingFlows = flowNode.getOutgoingFlows();

                // 执行你的计算逻辑
                // 注意：你需要将 caculateLinkNode 方法的第一个参数类型改为 List<SequenceFlow>
                caculateLinkNode(outgoingFlows, nodeList, processVariables, autoCalculate, null);
            }
        }
        return nodeList;

    }

    @Override
    public List<WorkflowTaskHistoryDto> getTaskHistory(String processInstanceId) {
        List<WorkflowTaskHistoryDto> result =
                historyService.createHistoricTaskInstanceQuery().processInstanceId(processInstanceId).taskDeleteReasonLike("completed").finished()
                        .list().stream().map(this::getTaskHistory).collect(toList());
        return userNameUtils.mapList(result, WorkflowTaskHistoryDto.class);
    }

    @Override
    public List<WorkflowTaskHistoryDto> getTaskHistories(List<String> processInstanceIds) {

        List<WorkflowTaskHistoryDto> result =
                historyService.createHistoricTaskInstanceQuery().processInstanceIdIn(processInstanceIds).taskDeleteReasonLike("completed").finished().orderByHistoricTaskInstanceEndTime()
                        .asc().list().stream().map(this::getTaskHistory).collect(toList());
        return userNameUtils.mapList(result, WorkflowTaskHistoryDto.class);
    }

    @Override
    public List<WorkflowTaskDetailDto> getCurrTasksWithAssigneeInfos(String processInstanceId) {
        List<WorkflowTaskDto> taskList = getTasksByProcess(new ArrayList<>() {{
            add(processInstanceId);
        }}, false);
        List<WorkflowTaskDetailDto> result = MapperUtil.mapList(taskList, mapper::workflowTaskDtoToWorkflowTaskDetailDto);
        List<UserNameDto> userNameList = new ArrayList<>();
        if (!result.isEmpty()) {
            for (WorkflowTaskDetailDto task : result) {
                String assigneeStr = task.getAssignee();
                List<String> roleNames = new ArrayList<>();
                if (StrUtil.isNotBlank(assigneeStr)) {
                    for (String assignee : assigneeStr.split(",")) {//兼容处理指定多用户的情况
                        if (NumberUtil.isLong(assignee)) {
                            UserNameDto nameDto = new UserNameDto();
                            nameDto.setProcessInstanceId(task.getId()); //这里用taskId
                            nameDto.setAssigneeId(Long.valueOf(assignee));
                            userNameList.add(nameDto);
                        } else {
                            roleNames.add(assignee);
                        }
                    }
                }
                task.setRoleNames(String.join(",", roleNames));
            }
        }
        List<UserNameDto> userNameDtos = userNameUtils.mapList(userNameList, UserNameDto.class);
        for (WorkflowTaskDetailDto item : result) {
            List<String> userNames = userNameDtos.stream().filter(p -> p.getProcessInstanceId().equals(item.getId()))
                    .map(UserNameDto::getAssigneeName).toList();

            if (!userNames.isEmpty()) {
                item.setUserNames(userNames.stream().distinct().collect(Collectors.joining(",")));
            }
        }

        return result;
    }

    @Override
    public boolean checkProcessCanRecallPre(String processInstanceId) {
        List<HistoricTaskInstance> historicTaskInstanceDesc = getHistoricTaskInstanceDesc(processInstanceId);
        ProcessDefinitionEntity processDefinitionEntity = getProcessDefinitionEntity(processInstanceId);
        List<Task> userTasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
        return checkProcessCanRecallPre(historicTaskInstanceDesc, processDefinitionEntity.getId(), userTasks);
    }

    ///**
    // * 驳回至上一节点
    // * @param processInstanceId
    // */
    //@Override
    //public void recallPre(String processInstanceId) {
    //    List<HistoricTaskInstance> historicTaskInstanceDesc = getHistoricTaskInstanceDesc(processInstanceId);
    //    ProcessDefinitionEntity processDefinitionEntity = getProcessDefinitionEntity(processInstanceId);
    //    List<Task> userTasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
    //    if (!checkProcessCanRecallPre(historicTaskInstanceDesc, processDefinitionEntity, userTasks)) {
    //        throw new BusinessException("当前节点无法撤回");
    //    }
    //    ActivityImpl gotoActivity = processDefinitionEntity.findActivity(historicTaskInstanceDesc.getFirst().getTaskDefinitionKey());
    //    ActivityImpl currActivity = processDefinitionEntity.findActivity(userTasks.getFirst().getTaskDefinitionKey());
    //    gotoAssignActivity(userTasks.getFirst(), currActivity, gotoActivity, CHEHUI);
    //    //删除历史记录
    //    historyService.deleteHistoricTaskInstance(historicTaskInstanceDesc.getFirst().getId());
    //    historyService.deleteHistoricTaskInstance(userTasks.getFirst().getId());
    //}

    @Override
    public void recallPre(String processInstanceId) {
        // 1. 获取历史任务（按结束时间倒序）
        List<HistoricTaskInstance> historicTaskInstanceDesc = getHistoricTaskInstanceDesc(processInstanceId);
        // 2. 获取当前运行中的任务
        List<Task> userTasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();

        // 校验逻辑（内部应改为基于 ID 的判断）
        if (!checkProcessCanRecallPre(historicTaskInstanceDesc, historicTaskInstanceDesc.getFirst().getProcessDefinitionId(), userTasks)) {
            throw new BusinessException("当前节点无法撤回");
        }

        String currActivityId = userTasks.getFirst().getTaskDefinitionKey();
        String targetActivityId = historicTaskInstanceDesc.getFirst().getTaskDefinitionKey();

        // 3. 执行跳转（撤回核心）
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdTo(currActivityId, targetActivityId)
                .changeState();

        // 4. 删除意见或处理历史（Flowable 7 自动结束当前任务历史，无需手动删除运行中任务的历史）
        historyService.deleteHistoricTaskInstance(historicTaskInstanceDesc.getFirst().getId());
        // 注意：currActivity 对应的历史任务在 changeState 时会被引擎自动更新/关闭
    }


    @Override
    public StartProcessOutDto startProcessAndCompleteFirst(StartProcessInputDto input) {
        WorkflowProcessDto processDto = mapper.inputDtoToWorkflowProcessDto(input);
        String processId = startNewProcess(processDto).getId();
        CompleteTaskByProcessIdInputDto taskDto = mapper.inputDtoToCompleteTaskByProcessIdInputDto(input);
        taskDto.setProcessInstanceId(processId);
        completeTaskByProcessId(taskDto);
        StartProcessOutDto result = new StartProcessOutDto();
        result.setProcessInstanceId(processId);
        List<String> params = new ArrayList<>();
        params.add(processId);
        result.setTaskInfos(getTasksByProcess(params));
        return result;
    }


    @Override
    @SneakyThrows
    public List<WorkflowTaskDto> completeTask(CompleteTaskInputDto input) {
        return completeTask(input, true);
    }


    @Override
    @SneakyThrows
    public List<WorkflowTaskDto> completeTask(CompleteTaskInputDto input, boolean checkAuth) {
        ProcessInstance processInstance =
                runtimeService.createProcessInstanceQuery().processInstanceId(input.getProcessInstanceId()).singleResult();
        if (processInstance == null) {
            throw new BusinessException("无法找到流程");
        }
        Map<String, Object> variables = input.getVariables();
        if (variables == null) {
            variables = new HashMap<>();
        }

        if (StrUtil.isNotEmpty(input.getTitle())) {
            variables.put(FlowableConstants.TITLE, input.getTitle());
        }
        if (StrUtil.isNotEmpty(input.getTypeName())) {
            variables.put(FlowableConstants.TYPE_NAME, input.getTypeName());
        }
        if (StrUtil.isNotEmpty(input.getType())) {
            variables.put(FlowableConstants.BUSINESS_TYPE, input.getType());
        }
        if (input.getFilterStation() != null) {
            variables.put(FlowableConstants.FILTER_STATION, input.getFilterStation());
        }
        if (StrUtil.isNotEmpty(input.getStarter())) {
            variables.put(FlowableConstants.STARTER, input.getStarter());
        }

        input.setVariables(variables);
        completeTask(input.getTaskId(), input, checkAuth);
        List<String> params = new ArrayList<>();
        params.add(input.getProcessInstanceId());
        List<WorkflowTaskDto> tasksByProcess = getTasksByProcessAndInitAssignee(params, false);
        syncTodos(processInstance, tasksByProcess, mapper.completeTaskInputDtoToAppPushDto(input), input.getBusinessData());
        return tasksByProcess;
    }

    @Override
    public List<WorkflowTaskDto> getTasksByProcess(List<String> processInstanceIds, boolean checkAuth) {
        return getTasksByProcess(processInstanceIds, checkAuth, false);
    }

    @Override
    public List<WorkflowTaskDto> getTasksByProcess(List<String> processInstanceIds, boolean auth, boolean initAssignee) {
        List<Task> tasks = taskService.createTaskQuery().processInstanceIdIn(processInstanceIds).list();
        if (initAssignee) {
            for (Task task : tasks) {
                String assignee = task.getAssignee();
                if (FlowableConstants.PLACE_HOLDER_DIRECT_LEADER.equals(assignee)) {
                    // rpc 获取直属上级， 如果获取不到 直接抛出异常
                    Long superiorId = userRpcService.getSuperiorId(LoginUserIdContextHolder.getUserId());
                    if (superiorId == null) {
                        //故意与原生activiti报错提示一致 其实是个沙雕写法
                        throw new BusinessException("can not find the leader of current user");
                    }
                    task.setAssignee(String.valueOf(superiorId));
                    taskService.saveTask(task);
                }
            }
        }
        return toTaskDto(tasks, auth);
    }

    @Override
    public List<WorkflowTaskDto> getTasksByProcessAndInitAssignee(List<String> processInstanceIds, boolean auth) {
        return getTasksByProcess(processInstanceIds, auth, true);
    }

    @Override
    public void updateFlowVariables(UpdateFlowVariablesInput input) {
        Map<String, Object> variables = input.getVariables();
        if (variables == null || variables.isEmpty()) {
            throw new BusinessException("variables不能为空！");
        }
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(input.getProcessInstanceId()).singleResult();
        if (processInstance == null) {
            throw new BusinessException("流程不存在或已结束");
        }

        runtimeService.setVariables(input.getProcessInstanceId(), variables);

        // 3. 获取 BPMN 模型解析代理人表达式
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(input.getProcessInstanceId()).list();
        List<Task> updatedTasks = new ArrayList<>();
        Map<String, Object> currVariables = runtimeService.getVariables(input.getProcessInstanceId());

        for (Task task : tasks) {
            // 通过 BpmnModel 获取 UserTask
            FlowElement flowElement = bpmnModel.getFlowElement(task.getTaskDefinitionKey());
            if (flowElement instanceof UserTask userTask) {
                String assigneeExp = userTask.getAssignee(); // 获取原生的表达式字符串，如 ${manager}

                if (StrUtil.isNotBlank(assigneeExp)) {
                    for (String key : variables.keySet()) {
                        // 只要表达式包含更新的 key，则重新解析
                        if (assigneeExp.contains("${" + key + "}")) {
                            String newAssignee = getExpressionValue(assigneeExp, currVariables);
                            if (StrUtil.isBlank(newAssignee)) {
                                throw new BusinessException("代理人不能为空");
                            }
                            // 4. 使用官方 API 变更代理人（saveTask 不推荐用于变更 assignee）
                            taskService.setAssignee(task.getId(), newAssignee);
                            task.setAssignee(newAssignee); // 同步内存对象用于后续 DTO 转换
                            updatedTasks.add(task);
                            break;
                        }
                    }
                }
            }
        }


        //List<Task> tasks = taskService.createTaskQuery().processInstanceId(input.getProcessInstanceId()).list();
        //
        //List<Task> updatedTasks = new ArrayList<>();
        //
        //Map<String, Object> currVariables = runtimeService.getVariables(input.getProcessInstanceId());
        //String definitionId = processInstance.getProcessDefinitionId();
        //ProcessDefinitionEntity processDefinitionEntity =
        //        (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(definitionId);
        //for (Task task : tasks) {
        //    TaskDefinition taskDefinition = processDefinitionEntity.getTaskDefinitions().get(task.getTaskDefinitionKey());
        //    if (taskDefinition != null && taskDefinition.getAssigneeExpression() != null) {
        //        for (String key : variables.keySet()) {
        //            String assigneeExpression = taskDefinition.getAssigneeExpression().getExpressionText();
        //            if (StrUtil.isNotBlank(assigneeExpression) && assigneeExpression.contains("${" + key + "}")) {
        //                String assignee = getExpressionValue(assigneeExpression, currVariables);
        //                if (StrUtil.isBlank(assignee)) {
        //                    throw new BusinessException("代理人不能为空");
        //                }
        //                //变更代理人
        //                task.setAssignee(assignee);
        //                taskService.saveTask(task);
        //                updatedTasks.add(task);
        //                break;
        //            }
        //        }
        //    }
        //}
        //更新待办
        if (!updatedTasks.isEmpty()) {
            syncTodosAssigneeChanged(processInstance, toTaskDto(updatedTasks, false), mapper.updateFlowVariablesInputToAppPushDto(input), input.getBusinessData());
        }
    }

    //region private method

    /**
     * 流程是否已经结束
     *
     * @param processInstanceId 流程实例ID
     */
    private boolean isFinished(String processInstanceId) {
        return historyService.createHistoricProcessInstanceQuery().finished().processInstanceId(processInstanceId).count() > 0;
    }

    private String getTaskIdByProcessInstanceId(String processInstanceId) {
        List<Task> list = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
        if (list == null || list.isEmpty()) {
            throw new RuntimeException("进程实例ID无法查询到关联的任务...");
        }
        if (list.size() == 1) {
            return list.getFirst().getId();
        } else {
            throw new RuntimeException("当前进程实例有多个任务在运行中...");
        }
    }

    private WorkflowTaskDto getWorkflowTaskDto(UserTask userTask, Map<String, Object> processVariables) {
        WorkflowTaskDto workflowTaskDto = new WorkflowTaskDto();

        // 1. 基础属性获取（v7 中 getDocumentation 对应描述）
        workflowTaskDto.setName(resolve(userTask.getName(), processVariables));
        workflowTaskDto.setDescription(resolve(userTask.getDocumentation(), processVariables));
        workflowTaskDto.setAssignee(resolve(userTask.getAssignee(), processVariables));
        workflowTaskDto.setOwner(resolve(userTask.getOwner(), processVariables));
        workflowTaskDto.setFormKey(resolve(userTask.getFormKey(), processVariables));

        List<WorkflowIdentityLinkDto> identityLinks = new ArrayList<>();

        // 2. 处理候选人 (CandidateUsers 现在是 List<String>)
        if (CollUtil.isNotEmpty(userTask.getCandidateUsers())) {
            for (String userExp : userTask.getCandidateUsers()) {
                WorkflowIdentityLinkDto dto = new WorkflowIdentityLinkDto();
                dto.setUserId(resolve(userExp, processVariables));
                dto.setType(IdentityLinkType.CANDIDATE);
                identityLinks.add(dto);
            }
        }

        // 3. 处理候选组 (CandidateGroups 现在是 List<String>)
        if (CollUtil.isNotEmpty(userTask.getCandidateGroups())) {
            for (String groupExp : userTask.getCandidateGroups()) {
                WorkflowIdentityLinkDto dto = new WorkflowIdentityLinkDto();
                dto.setGroupId(resolve(groupExp, processVariables));
                dto.setType(IdentityLinkType.CANDIDATE);
                identityLinks.add(dto);
            }
        }

        workflowTaskDto.setIdentityLinks(identityLinks);
        return workflowTaskDto;
    }

    /** 辅助方法：统一处理 String 类型的表达式解析 */
    private String resolve(String expression, Map<String, Object> vars) {
        if (expression == null) return null;
        // 调用你原有的表达式解析逻辑，或使用 Flowable 7 的 ManagementService 解析
        return getExpressionValue(expression, vars);
    }


    private RelatedNodeInfo getWorkflowNodeDto(UserTask userTask, Map<String, Object> processVariables) {
        RelatedNodeInfo nodeInfo = new RelatedNodeInfo();
        // 1. 设置节点类型（建议使用 Flowable 7 自带常量或自定义常量）
        nodeInfo.setNodeType("userTask");

        // 2. 获取名称：v7 中 getName() 返回的是 String（可能是表达式字符串）
        nodeInfo.setName(userTask.getName() != null ?
                getExpressionValue(userTask.getName(), processVariables) : null);

        // 3. 获取 FormKey：直接从 UserTask 对象获取 String 类型的 Key
        nodeInfo.setFormKey(userTask.getFormKey() != null ?
                getExpressionValue(userTask.getFormKey(), processVariables) : null);

        return nodeInfo;
    }

    /**
     * 根据表达式获取值
     *
     * @param expression 表达式
     * @param variables 变量
     */
    private String getExpressionValue(String expression, Map<String, Object> variables) {
        ExpressionFactory factory = new ExpressionFactoryImpl();
        SimpleContext context = new SimpleContext();

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            ValueExpression valueExpression = factory.createValueExpression(entry.getValue(), String.class);
            context.setVariable(entry.getKey(), valueExpression);
        }
        try {
            ValueExpression e = factory.createValueExpression(context, expression, String.class);
            return e.getValue(context).toString();
        } catch (Exception ex) {
            log.error(ex.getMessage());
            return expression;
        }
    }

    /**
     * 获取下一个任务节点
     *
     * @param flowElement 流程节点信息
     * @param currentActivityId   当前流程节点Id信息
     * @param variables    变量
     */
    private UserTask nextTaskDefinition(FlowElement flowElement, String currentActivityId, Map<String, Object> variables) {
        // 1. 如果当前节点是用户任务，且不是起点节点，则为目标节点
        if (flowElement instanceof UserTask && !flowElement.getId().equals(currentActivityId)) {
            return (UserTask) flowElement;
        }

        // 2. 如果是连线节点 (FlowNode)，处理其出线 (OutgoingFlows)
        if (flowElement instanceof FlowNode) {
            List<SequenceFlow> outgoingFlows = ((FlowNode) flowElement).getOutgoingFlows();

            for (SequenceFlow sf : outgoingFlows) {
                String condition = sf.getConditionExpression();
                // 3. 评估连线条件（如果没有条件或满足 EL 表达式）
                if (StrUtil.isEmpty(condition) || (variables != null && isCondition(condition, variables))) {
                    FlowElement target = sf.getTargetFlowElement();
                    UserTask found = nextTaskDefinition(target, currentActivityId, variables);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }


    /**
     * 根据变量返回el表达式是否通过信息
     *
     * @param el        表达式
     * @param variables 变量
     * @return 表达式是否为真
     */
    private boolean isCondition(String el, Map<String, Object> variables) {
        ExpressionFactory factory = new ExpressionFactoryImpl();
        SimpleContext context = new SimpleContext();

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            ValueExpression valueExpression = factory.createValueExpression(entry.getValue(), String.class);
            context.setVariable(entry.getKey(), valueExpression);
        }
        ValueExpression e = factory.createValueExpression(context, el, boolean.class);
        boolean flag = false;
        try {
            flag = (Boolean) e.getValue(context);
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return flag;
    }

    /**
     * 计算关联节点
     */
    private void caculateLinkTask(List<SequenceFlow> outgoingFlows, List<WorkflowTaskDto> taskList,
                                  Map<String, Object> processVariables, boolean autoCalculate, String defaultFlowId) {
        WorkflowTaskDto defaultWorkflow = null;

        for (SequenceFlow sf : outgoingFlows) {
            FlowElement target = sf.getTargetFlowElement();

            // 1. 处理用户任务节点
            if (target instanceof UserTask userTask) {
                WorkflowTaskDto dto = getWorkflowTaskDto(userTask, processVariables);

                // 获取连线名称 (对应原 flowName)
                if (StrUtil.contains(sf.getName(), BOHUI)) {
                    dto.setTaskDirection("from");
                    taskList.add(dto);
                } else {
                    if (autoCalculate) {
                        if (sf.getId().equals(defaultFlowId)) {
                            dto.setTaskDirection("to");
                            defaultWorkflow = dto;
                        } else {
                            // 获取 EL 表达式 (对应原 conditionText)
                            String condition = sf.getConditionExpression();
                            if (StrUtil.isEmpty(condition) || isCondition(condition, processVariables)) {
                                dto.setTaskDirection("to");
                                taskList.add(dto);
                            }
                        }
                    } else {
                        dto.setTaskDirection("to");
                        taskList.add(dto);
                    }
                }
            }
            // 2. 处理网关递归 (ParallelGateway, ExclusiveGateway 等均继承自 Gateway)
            else if (target instanceof Gateway gateway) {
                caculateLinkTask(gateway.getOutgoingFlows(), taskList, processVariables, autoCalculate, gateway.getDefaultFlow());
            }
        }

        // 3. 兜底处理默认连线
        if (autoCalculate && StringUtils.isNotEmpty(defaultFlowId) && defaultWorkflow != null) {
            if (taskList.stream().noneMatch(t -> "to".equals(t.getTaskDirection()))) {
                taskList.add(defaultWorkflow);
            }
        }
        taskList.forEach(e -> e.setProcessVariables(processVariables));
    }



    private void caculateLinkNode(List<SequenceFlow> outgoingFlows, List<RelatedNodeInfo> nodeList,
                                  Map<String, Object> processVariables, boolean autoCalculate, String defaultFlowId) {
        RelatedNodeInfo defaultWorkflow = null;

        for (SequenceFlow sf : outgoingFlows) {
            FlowElement target = sf.getTargetFlowElement();

            // 1. 处理用户任务 (UserTask)
            if (target instanceof UserTask userTask) {
                // 这里的 getWorkflowNodeDto 需改为接收 UserTask 对象
                RelatedNodeInfo nodeInfo = getWorkflowNodeDto(userTask, processVariables);

                // 使用 sf.getName() 替代 getProperty("name")
                if (StrUtil.contains(sf.getName(), BOHUI)) {
                    nodeInfo.setTaskDirection("from");
                    nodeList.add(nodeInfo);
                } else {
                    if (autoCalculate) {
                        if (sf.getId().equals(defaultFlowId)) {
                            nodeInfo.setTaskDirection("to");
                            defaultWorkflow = nodeInfo;
                        } else {
                            String condition = sf.getConditionExpression();
                            if (StrUtil.isEmpty(condition) || isCondition(condition, processVariables)) {
                                nodeInfo.setTaskDirection("to");
                                nodeList.add(nodeInfo);
                            }
                        }
                    } else {
                        nodeInfo.setTaskDirection("to");
                        nodeList.add(nodeInfo);
                    }
                }
            }
            // 2. 处理网关 (Gateway)
            else if (target instanceof Gateway gateway) {
                // 递归传入网关的出线和默认流 ID
                caculateLinkNode(gateway.getOutgoingFlows(), nodeList, processVariables,
                        autoCalculate, gateway.getDefaultFlow());
            }
            // 3. 处理结束节点 (EndEvent)
            else if (target instanceof EndEvent) {
                RelatedNodeInfo endNode = new RelatedNodeInfo();
                endNode.setNodeType("endEvent"); // 或使用你的常量

                if (autoCalculate) {
                    String condition = sf.getConditionExpression();
                    if (StrUtil.isEmpty(condition) || isCondition(condition, processVariables)) {
                        nodeList.add(endNode);
                    }
                } else {
                    nodeList.add(endNode);
                }
            }
        }

        // 4. 兜底处理默认流
        if (autoCalculate && StringUtils.isNotEmpty(defaultFlowId) && defaultWorkflow != null) {
            if (nodeList.stream().noneMatch(t -> "to".equals(t.getTaskDirection()))) {
                nodeList.add(defaultWorkflow);
            }
        }
    }


    private WorkflowTaskHistoryDto getTaskHistory(HistoricTaskInstance query) {
        WorkflowTaskHistoryDto workflowTaskHistoryDto = new WorkflowTaskHistoryDto();
        BeanUtils.copyProperties(query, workflowTaskHistoryDto);
        boolean isNumberAssign = NumberUtil.isLong(query.getAssignee());
        if (isNumberAssign) {
            workflowTaskHistoryDto.setAssigneeId(Long.valueOf(query.getAssignee()));
        }

        // 获取审批意见
        List<Comment> taskComments = taskService.getTaskComments(query.getId());
        if (taskComments != null && !taskComments.isEmpty()) {
            workflowTaskHistoryDto.setComment(taskComments.getFirst().getFullMessage());
        }

        // 获取审批的变量
        List<HistoricVariableInstance> historicVariableInstances =
                historyService.createHistoricVariableInstanceQuery().taskId(query.getId()).list();
        Map<String, Object> variables = new HashMap<>();
        for (HistoricVariableInstance historicVariableInstance : historicVariableInstances) {
            variables.put(historicVariableInstance.getVariableName(), historicVariableInstance.getValue());
        }
        workflowTaskHistoryDto.setVariables(variables);
        workflowTaskHistoryDto.setBeginTime(DateUtils.toLocalDateTime(query.getClaimTime()));
        workflowTaskHistoryDto.setStopTime(DateUtils.toLocalDateTime(query.getEndTime()));
        return workflowTaskHistoryDto;
    }


    /**
     * 判断userTask是否有权限
     *
     * @param task 当前任务
     * @return 是否具有权限
     */
    private boolean hasTaskPermission(Task task) {
        String checkAssignee = task.getAssignee();
        if (StringUtils.isBlank(checkAssignee) || LoginUserIdContextHolder.getUserId() == null) {
            return false;
        }
        List<String> assignees = getFormKeyAssignee(task.getFormKey());
        assignees.add(checkAssignee);
        UserFullListDto userInfo = userRpcService.getUserFullById(securityUtils.getCurrentUser().getId());
        return hasTaskPermission(assignees, userInfo);
    }

    /**
     * 获取formKey补充角色或者人员
     */
    private List<String> getFormKeyAssignee(String formKey) {
        List<String> assignee = new ArrayList<>();
        TaskFormKey taskFormKey = getTaskFormKey(formKey);
        //存在角色组
        if (StrUtil.isNotBlank(taskFormKey.getActiviti().getCandidateRoles())) {
            assignee.add(taskFormKey.getActiviti().getCandidateRoles());
        }
        //存在人员组
        if (StrUtil.isNotBlank(taskFormKey.getActiviti().getCandidatePsns())) {
            assignee.add(taskFormKey.getActiviti().getCandidatePsns());
        }
        return assignee;
    }

    private boolean checkAssignee(String assignee, UserFullListDto userInfo) {
        if (!StrUtil.isEmpty(assignee)) {
            String[] assignees = assignee.split(",");
            List<String> assigneeList = Arrays.stream(assignees).map(String::trim).toList();
            boolean isUser = assigneeList.stream().anyMatch(e -> e.equals(userInfo.getId().toString()));
            long containRole =
                    userInfo.getUserRoles().stream().filter(p -> assigneeList.contains(p.getRoleName())).count();
            boolean isRole = containRole != 0;
            return isUser || isRole;
        }
        return true;
    }

    private boolean hasTaskPermission(List<String> assignees, UserFullListDto userInfo) {
        boolean hasPermission = false;
        if (assignees != null && !assignees.isEmpty()) {
            for (String assignee : assignees) {
                hasPermission = checkAssignee(assignee, userInfo);
                if (hasPermission) {
                    break;
                }
            }
        }
        return hasPermission;
    }

    private WorkflowTaskDto task2TaskDto(Task task, boolean checkAuth) {
        if (checkAuth) {
            boolean hasTaskPermission = hasTaskPermission(task);
            if (!hasTaskPermission) {
                return null;
            }
        }
        WorkflowTaskDto workflowTaskDto = new WorkflowTaskDto();
        BeanUtils.copyProperties(task, workflowTaskDto, "identityLinks");
        // 获取任务关联自定义表格
        TaskFormData taskFormData = formService.getTaskFormData(task.getId());
        List<FormProperty> formProperties = taskFormData.getFormProperties();
        String formKey = taskFormData.getFormKey();
        workflowTaskDto.setFormProperties(MapperUtil.mapList(formProperties, mapper::ToFormPropertyDto));
        workflowTaskDto.setFormKey(formKey);
        workflowTaskDto.setDefinitionKey(task.getTaskDefinitionKey());

        // 获取当人任务的候选人列表
        List<IdentityLink> identityLinks = taskService.getIdentityLinksForTask(task.getId());
        List<WorkflowIdentityLinkDto> linksList = new ArrayList<>();
        for (IdentityLink identityLink : identityLinks) {
            WorkflowIdentityLinkDto workflowIdentityLinkDto = new WorkflowIdentityLinkDto();
            BeanUtils.copyProperties(identityLink, workflowIdentityLinkDto);
            linksList.add(workflowIdentityLinkDto);
        }
        workflowTaskDto.setIdentityLinks(linksList);
        return workflowTaskDto;
    }

    private ProcessInstance startNewProcess(WorkflowProcessDto workflowProcessDto) {
        try {
            Map<String, Object> variables = workflowProcessDto.getVariables();
            if (variables == null) {
                variables = new HashMap<>();
            }
            variables.put(FlowableConstants.TITLE, workflowProcessDto.getTitle());
            variables.put(FlowableConstants.TYPE_NAME, workflowProcessDto.getTypeName());
            variables.put(FlowableConstants.BUSINESS_TYPE, workflowProcessDto.getType());
            variables.put(FlowableConstants.FILTER_STATION, workflowProcessDto.isFilterStation());
            //填充发起人
            if (StrUtil.isEmpty(workflowProcessDto.getStarter()) && LoginUserIdContextHolder.getUserId() != null) {
                UserFullListDto currUser = userRpcService.getUserFullById(LoginUserIdContextHolder.getUserId());
                variables.put(FlowableConstants.STARTER, currUser.getName());
            } else {
                variables.put(FlowableConstants.STARTER, workflowProcessDto.getStarter());
            }

            return runtimeService
                    .startProcessInstanceByKeyAndTenantId(workflowProcessDto.getProcessDefinitionKey(),
                            workflowProcessDto.getBusinessKey(), variables,
                            String.valueOf(TenantContextHolder.getTenantId()));
        } catch (Exception ex) {
            throw new BusinessException(ex.getMessage());
        }
    }

    //同步待办
    private void syncTodos(ProcessInstance processInstance, List<WorkflowTaskDto> tasks, AppPushDto pushDto, Map<String, Object> businessData) {
        List<ToDoDto> all = toTodoList(processInstance, tasks, pushDto, businessData);
        todoRpcService.syncActivitiTask(processInstance.getId(), all);
    }

    //代理人变更重新推送待办
    private void syncTodosAssigneeChanged(ProcessInstance processInstance, List<WorkflowTaskDto> tasks, AppPushDto pushDto, Map<String, Object> businessData) {
        List<ToDoDto> all = toTodoList(processInstance, tasks, pushDto, businessData);
        todoRpcService.syncActivitiTaskAssigneeChanged(processInstance.getId(), all);
    }

    private List<ToDoDto> toTodoList(ProcessInstance processInstance, List<WorkflowTaskDto> tasks, AppPushDto pushDto, Map<String, Object> businessData) {
        List<ToDoDto> all = new ArrayList<>();
        if (tasks != null && !tasks.isEmpty()) {
            Map<String, Object> variables = runtimeService.getVariables(processInstance.getId());
            tasks.forEach(p -> {
                String title = variables.get(FlowableConstants.TITLE).toString();
                String typeName = variables.get(FlowableConstants.TYPE_NAME).toString();
                String type = FlowableConstants.TYPE;
                String state = p.getName();
                String businessType = variables.get(FlowableConstants.BUSINESS_TYPE).toString();
                boolean filterStation = (Boolean) variables.get(FlowableConstants.FILTER_STATION);
                TaskFormKey taskFormKey = getTaskFormKey(p.getFormKey());
                boolean addTodo = taskFormKey.getActiviti().getNotice().isAddTodo();
                boolean appPush = taskFormKey.getActiviti().getNotice().isAppPush();
                String starter = (String) variables.get(FlowableConstants.STARTER);
                String businessId = processInstance.getId() + "|" + p.getId();
                ToDoTargetType toDoTargetType = ToDoTargetType.ROLE;
                if (p.getAssigneeType().equals(AssigneeTypeEnum.UserId)) {
                    toDoTargetType = ToDoTargetType.USER_ID;
                }

                TodoExtensionsDto extensionsDto = new TodoExtensionsDto();
                extensionsDto.setBusinessId(processInstance.getBusinessKey());
                extensionsDto.setType(businessType);
                extensionsDto.setBusinessData(businessData);
                String extensions = "";
                try {
                    extensions = objectMapper.writeValueAsString(extensionsDto);
                } catch (JsonProcessingException e) {
                    log.error("序列化对象异常", e);
                }
                if (StrUtil.isNotEmpty(p.getAssignee()) && addTodo) {
                    String[] assignees = StrUtil.split(p.getAssignee(), (int) ',');
                    ToDoDto toDoDto = new ToDoDto(type, typeName, title, state, toDoTargetType, assignees, businessId
                            , filterStation, extensions);
                    if (pushDto != null) {
                        toDoDto.setAutoAppPush(appPush);
                        toDoDto.setAppBody(pushDto.getAppBody());
                        toDoDto.setAppTitle(pushDto.getAppTitle());
                        toDoDto.setNavigation(pushDto.getNavigation());
                        toDoDto.setNoticationLevel(pushDto.getNoticationLevel());
                        toDoDto.setPushType(pushDto.getPushType());
                        toDoDto.setSubType(extensionsDto.getType());
                        toDoDto.setSubBusinessId(extensionsDto.getBusinessId());
                    }
                    toDoDto.setStarter(starter);
                    all.add(toDoDto);
                }
            });
        }
        return all;
    }


    private List<WorkflowTaskDto> toTaskDto(List<Task> tasks, boolean checkAuth) {
        List<WorkflowTaskDto> list = new ArrayList<>();
        tasks.forEach(p -> {
            WorkflowTaskDto workflowTaskDto = task2TaskDto(p, checkAuth);
            if (workflowTaskDto != null) {
                list.add(workflowTaskDto);
            }
        });
        return list;
    }

    private TaskFormKey getTaskFormKey(String formKeyStr) {
        if (StrUtil.isBlank(formKeyStr)) {
            formKeyStr = "{}";
        }
        try {
            return objectMapper.readValue(formKeyStr, TaskFormKey.class);
        } catch (Exception e) {
            log.error("formKey配置有误！formKey={}", formKeyStr);
            return new TaskFormKey();
        }
    }


    /**
     * 跳转到指定的userTask节点
     * 模式转变：不再需要手动“清除出线 -> 创建临时出线 -> 恢复出线”这种 hack 操作。
     * API 变更：使用 runtimeService.createChangeActivityStateBuilder()，这是 Flowable 7 处理“节点跳转”、“撤回”、“驳回”的标准方式。
     * 安全性：原生 API 会自动处理任务的销毁、并发路径的清理以及历史日志的更新，避免了旧版本中由于手动操作出线导致的数据库状态不一致问题。
     * 参数简化：不再需要 ActivityImpl 对象，只需传入节点的 ActivityId (即 BPMN 文件中的 ID 字符串) 即可。
     */
    private void gotoAssignActivity(Task task, String gotoActivityId, String message) {
        // 1. 添加审批意见（如果需要）
        if (StrUtil.isNotEmpty(message)) {
            taskService.addComment(task.getId(), task.getProcessInstanceId(), message);
        }

        // 2. 执行跳转：通过当前节点 ID 直接跳转到目标节点 ID
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(task.getProcessInstanceId())
                .moveActivityIdTo(task.getTaskDefinitionKey(), gotoActivityId) // 从当前任务节点跳转到目标节点
                .changeState();

        // 注意：changeState() 会自动结束当前任务，不需要手动调用 completeTask
    }

    private boolean checkProcessCanRecallPre(List<HistoricTaskInstance> historicTaskInstances,
                                             String processDefinitionId,
                                             List<Task> userTasks) {
        if (historicTaskInstances.isEmpty()) return false;

        // 1. 获取上一个任务实例及当前用户校验
        HistoricTaskInstance lastTask = historicTaskInstances.getFirst();
        String nowUserId = String.valueOf(LoginUserIdContextHolder.getUserId());
        if (!lastTask.getAssignee().equals(nowUserId)) {
            return false;
        }

        // 2. 通过 BpmnModel 获取节点定义
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        FlowElement flowElement = bpmnModel.getFlowElement(lastTask.getTaskDefinitionKey());

        // 3. 判断是否为会签节点 (MultiInstance)
        if (flowElement instanceof UserTask lastUserTask) {
            if (lastUserTask.getLoopCharacteristics() != null) {
                return false; // 存在多实例循环特性即为会签
            }
        }

        // 4. 当前任务状态校验
        if (userTasks.size() == 1) {
            Task currTask = userTasks.getFirst();

            // 5. 校验 FormKey 中的自定义撤回标记 (保持你原有的 getTaskFormKey 逻辑)
            TaskFormKey taskFormKey = getTaskFormKey(currTask.getFormKey());
            if (taskFormKey == null || !taskFormKey.getActiviti().isCallBackPre()) {
                return false;
            }

            // 6. 禁止撤回到自身
            return !currTask.getTaskDefinitionKey().equals(lastTask.getTaskDefinitionKey());
        }

        return false;
    }

    private List<HistoricTaskInstance> getHistoricTaskInstanceDesc(String processInstanceId) {
        return historyService.createHistoricTaskInstanceQuery().processUnfinished().processInstanceId(processInstanceId).finished().orderByTaskCreateTime().desc().list();
    }

    private ProcessDefinitionEntity getProcessDefinitionEntity(String processInstanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        return (ProcessDefinitionEntity) repositoryService.getProcessDefinition(processInstance.getProcessDefinitionId());
    }
}
