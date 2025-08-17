package com.dusk.module.workflow.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.dusk.module.workflow.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dozermapper.core.Mapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.*;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.activiti.engine.impl.form.DefaultStartFormHandler;
import org.activiti.engine.impl.javax.el.ExpressionFactory;
import org.activiti.engine.impl.javax.el.ValueExpression;
import org.activiti.engine.impl.juel.ExpressionFactoryImpl;
import org.activiti.engine.impl.juel.SimpleContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmActivity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.pvm.process.TransitionImpl;
import org.activiti.engine.impl.task.TaskDefinition;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.*;
import org.activiti.image.ProcessDiagramGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import com.dusk.common.framework.auth.authentication.LoginUserIdContextHolder;
import com.dusk.common.framework.exception.BusinessException;
import com.dusk.common.framework.model.UserContext;
import com.dusk.common.framework.tenant.TenantContextHolder;
import com.dusk.common.framework.utils.DateUtils;
import com.dusk.common.framework.utils.DozerUtils;
import com.dusk.common.framework.utils.SecurityUtils;
import com.dusk.common.framework.utils.UserNameUtils;
import com.dusk.common.module.activiti.dto.*;
import com.dusk.common.module.activiti.service.IWorkFlowRpcService;
import com.dusk.common.module.auth.dto.ToDoDto;
import com.dusk.common.module.auth.dto.UserFullListDto;
import com.dusk.common.module.auth.enums.AssigneeTypeEnum;
import com.dusk.common.module.auth.enums.ToDoTargetType;
import com.dusk.common.module.auth.service.ITodoRpcService;
import com.dusk.common.module.auth.service.IUserRpcService;
import com.dusk.module.workflow.constant.ActivitiConstants;
import com.dusk.module.workflow.dto.*;
import com.dusk.module.workflow.service.IWorkflowService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * @author kefuming
 * @date 2020-07-22 16:15
 */
//显示配置dubbo provider 禁止consumer重试，超时时长为2秒
@Service(retries = 0, timeout = 3500)
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class WorkflowServiceImpl implements IWorkFlowRpcService, IWorkflowService {
    private static final String BOHUI = "驳回";
    private static final String CHEHUI = "撤回";

    @Autowired
    RuntimeService runtimeService;
    @Autowired
    TaskService taskService;
    @Autowired
    RepositoryService repositoryService;
    @Autowired
    ProcessEngineConfiguration processEngineConfiguration;
    @Autowired
    HistoryService historyService;
    @Autowired
    FormService formService;
    @Autowired
    Mapper dozerMapper;
    @Reference(timeout = 1500)
    IUserRpcService userRpcService;
    @Autowired
    SecurityUtils securityUtils;
    @Autowired
    UserNameUtils userNameUtils;
    @Reference
    ITodoRpcService todoRpcService;
    @Autowired
    ObjectMapper mapper;
    @Autowired
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
        syncTodos(processInstance, tasksByProcess, dozerMapper.map(workflowProcessDto, AppPushDto.class), workflowProcessDto.getBusinessData());
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
        List<String> activeActivityIds = new ArrayList<>(), highLightedFlows;

        // 获得历史活动记录实体
        List<HistoricActivityInstance> historicActivityInstances =
                historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId)
                        .orderByHistoricActivityInstanceStartTime().asc().list();

        // 获得当前活动的节点
        if (isFinished(processInstanceId)) {
            // 如果流程已经结束，则得到结束节点
            // activeActivityIds = historicActivityInstances.stream().map(HistoricActivityInstance::getActivityId)
            // .collect(Collectors.toList());

        } else {
            // 如果流程没有结束，则取当前活动节点
            // 根据流程实例ID获得当前处于活动状态的ActivityId合集
            activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
        }

        if (activeActivityIds.size() == 0) {
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
                processEngineConfiguration.getClassLoader(), 1.0);
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
        if (processIds != null && processIds.size() > 0) {
            UserContext currentUser = securityUtils.getCurrentUser();
            UserFullListDto userInfo = currentUser == null ? null : userRpcService.getUserFullById(currentUser.getId());
            List<Task> collect = new ArrayList<>(taskService.createTaskQuery().processInstanceIdIn(processIds).list());
            for (String processId : processIds) {
                ProcessDesOutPutDto dto = new ProcessDesOutPutDto();
                dto.setProcessInstanceId(processId);
                List<Task> relatedTask = collect.stream().filter(p -> p.getProcessInstanceId().equals(processId)).collect(toList());
                if (relatedTask.size() > 0) {
                    Map<String, Object> variables = runtimeService.getVariables(processId);
                    dto.setVariables(variables);
                    List<String> assignees = relatedTask.stream().filter(e -> StrUtil.isNotEmpty(e.getAssignee())).map(TaskInfo::getAssignee).collect(toList());
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
                    relatedTask.forEach(p -> {
                        assignees.addAll(getFormKeyAssignee(p.getFormKey()));
                    });
                    dto.setHasPermission(userInfo == null ? false : hasTaskPermission(assignees, userInfo));
                    String taskName = ArrayUtil.join(relatedTask.stream().map(TaskInfo::getName).distinct().collect(toList()).toArray(), "/");
                    dto.setTaskName(taskName);
                    Task task = relatedTask.get(0);//默认只有一个节点
                    dto.setFormKey(task.getFormKey());
                } else {
                    dto.setFinished(true);
                }
                data.add(dto);
            }

            List<UserNameDto> userNameDtos = userNameUtils.mapList(all, UserNameDto.class);
            for (ProcessDesOutPutDto item : data) {
                if (!item.isFinished()) {
                    List<String> temp = userNameDtos.stream().filter(p -> p.getProcessInstanceId().equals(item.getProcessInstanceId())).map(p -> p.getAssigneeName())
                            .collect(toList());
                    if (temp.size() > 0) {
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
            String expressionText =
                    ((DefaultStartFormHandler) processDefinition.getStartFormHandler()).getFormKey().getExpressionText();
            return expressionText;
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
        syncTodos(processInstance, getTasksByProcessAndInitAssignee(params, false), dozerMapper.map(input, AppPushDto.class), input.getBusinessData());
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
        syncTodosAssigneeChanged(processInstance, toTaskDto(Collections.singletonList(task), false), dozerMapper.map(input, AppPushDto.class), input.getBusinessData());
    }

    @Override
    public WorkflowTaskDto getNextTaskByProcessId(String processInstanceId, Map<String, Object> variables) {
        String taskId = getTaskIdByProcessInstanceId(processInstanceId);
        return getNextTask(taskId, variables);
    }

    @Override
    public WorkflowTaskDto getNextTask(String taskId, Map<String, Object> variables) {
        TaskDefinition task = null;

        Task taskInstance = taskService.createTaskQuery().taskId(taskId).singleResult();
        ProcessInstance processInstance =
                runtimeService.createProcessInstanceQuery().processInstanceId(taskInstance.getProcessInstanceId()).singleResult();

        //获取流程发布Id信息
        String definitionId = processInstance.getProcessDefinitionId();
        Map<String, Object> processVariables = new HashMap<>();

        try {
            processVariables = runtimeService.getVariables(taskInstance.getExecutionId());
        } catch (Exception e) {
            e.printStackTrace();
        }

        ProcessDefinitionEntity processDefinitionEntity =
                (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(definitionId);

        ExecutionEntity execution = (ExecutionEntity) processInstance;

        //当前流程节点Id信息
        String activitiId = execution.getActivityId();

        //获取流程所有节点信息
        List<ActivityImpl> activitiList = processDefinitionEntity.getActivities();

        //遍历所有节点信息
        for (ActivityImpl activityImpl : activitiList) {
            String id = activityImpl.getId();
            if (activitiId.equals(id)) {
                //获取下一个节点信息
                task = nextTaskDefinition(activityImpl, activityImpl.getId(), variables);
                break;
            }
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
        List<ActivityImpl> activityList = processDefinitionEntity.getActivities();

        //遍历所有节点信息
        for (ActivityImpl activityImpl : activityList) {
            String id = activityImpl.getId();
            if (activityId.equals(id)) {
                for (HistoricActivityInstance historicActivityInstance : historicActivityInstanceList) {
                    if (activityId.equals(historicActivityInstance.getActivityId())) {
                        break;
                    }
                }
                List<PvmTransition> outgoingTransitions = activityImpl.getOutgoingTransitions();
                caculateLinkTask(outgoingTransitions, taskList, processVariables, autoCalculate, null);
                break;
            }
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
                startEvent.setNodeType(ActivitiConstants.NODE_TYPE_START_EVENT);
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

            //获取流程所有节点信息
            List<ActivityImpl> activityList = processDefinitionEntity.getActivities();

            //遍历所有节点信息
            for (ActivityImpl activityImpl : activityList) {
                String id = activityImpl.getId();
                if (activityId.equals(id)) {
                    for (HistoricActivityInstance historicActivityInstance : historicActivityInstanceList) {
                        if (activityId.equals(historicActivityInstance.getActivityId())) {
                            break;
                        }
                    }
                    List<PvmTransition> outgoingTransitions = activityImpl.getOutgoingTransitions();
                    caculateLinkNode(outgoingTransitions, nodeList, processVariables, autoCalculate, null);
                    break;
                }
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
        List<WorkflowTaskDetailDto> result = DozerUtils.mapList(dozerMapper, taskList, WorkflowTaskDetailDto.class);
        List<UserNameDto> userNameList = new ArrayList<>();
        if (result.size() > 0) {
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
                    .map(UserNameDto::getAssigneeName).collect(toList());

            if (userNames.size() > 0) {
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
        return checkProcessCanRecallPre(historicTaskInstanceDesc, processDefinitionEntity, userTasks);
    }

    @Override
    public void recallPre(String processInstanceId) {
        List<HistoricTaskInstance> historicTaskInstanceDesc = getHistoricTaskInstanceDesc(processInstanceId);
        ProcessDefinitionEntity processDefinitionEntity = getProcessDefinitionEntity(processInstanceId);
        List<Task> userTasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
        if (!checkProcessCanRecallPre(historicTaskInstanceDesc, processDefinitionEntity, userTasks)) {
            throw new BusinessException("当前节点无法撤回");
        }
        ActivityImpl gotoActivity = processDefinitionEntity.findActivity(historicTaskInstanceDesc.get(0).getTaskDefinitionKey());
        ActivityImpl currActivity = processDefinitionEntity.findActivity(userTasks.get(0).getTaskDefinitionKey());
        gotoAssignActivity(userTasks.get(0), currActivity, gotoActivity, CHEHUI);
        //删除历史记录
        historyService.deleteHistoricTaskInstance(historicTaskInstanceDesc.get(0).getId());
        historyService.deleteHistoricTaskInstance(userTasks.get(0).getId());
    }


    @Override
    public StartProcessOutDto startProcessAndCompleteFirst(StartProcessInputDto input) {
        WorkflowProcessDto processDto = dozerMapper.map(input, WorkflowProcessDto.class);
        String processId = startNewProcess(processDto).getId();
        CompleteTaskByProcessIdInputDto taskDto = dozerMapper.map(input, CompleteTaskByProcessIdInputDto.class);
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
            variables.put(ActivitiConstants.TITLE, input.getTitle());
        }
        if (StrUtil.isNotEmpty(input.getTypeName())) {
            variables.put(ActivitiConstants.TYPE_NAME, input.getTypeName());
        }
        if (StrUtil.isNotEmpty(input.getType())) {
            variables.put(ActivitiConstants.BUSINESS_TYPE, input.getType());
        }
        if (input.getFilterStation() != null) {
            variables.put(ActivitiConstants.FILTER_STATION, input.getFilterStation().booleanValue());
        }
        if (StrUtil.isNotEmpty(input.getStarter())) {
            variables.put(ActivitiConstants.STARTER, input.getStarter());
        }

        input.setVariables(variables);
        completeTask(input.getTaskId(), input, checkAuth);
        List<String> params = new ArrayList<>();
        params.add(input.getProcessInstanceId());
        List<WorkflowTaskDto> tasksByProcess = getTasksByProcessAndInitAssignee(params, false);
        syncTodos(processInstance, tasksByProcess, dozerMapper.map(input, AppPushDto.class), input.getBusinessData());
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
                if (ActivitiConstants.PLACE_HOLDER_DIRECT_LEADER.equals(assignee)) {
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

        List<Task> tasks = taskService.createTaskQuery().processInstanceId(input.getProcessInstanceId()).list();

        List<Task> updatedTasks = new ArrayList<>();

        Map<String, Object> currVariables = runtimeService.getVariables(input.getProcessInstanceId());
        String definitionId = processInstance.getProcessDefinitionId();
        ProcessDefinitionEntity processDefinitionEntity =
                (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(definitionId);
        for (Task task : tasks) {
            TaskDefinition taskDefinition = processDefinitionEntity.getTaskDefinitions().get(task.getTaskDefinitionKey());
            if (taskDefinition != null && taskDefinition.getAssigneeExpression() != null) {
                for (String key : variables.keySet()) {
                    String assigneeExpression = taskDefinition.getAssigneeExpression().getExpressionText();
                    if (StrUtil.isNotBlank(assigneeExpression) && assigneeExpression.contains("${" + key + "}")) {
                        String assignee = getExpressionValue(assigneeExpression, currVariables);
                        if (StrUtil.isBlank(assignee)) {
                            throw new BusinessException("代理人不能为空");
                        }
                        //变更代理人
                        task.setAssignee(assignee);
                        taskService.saveTask(task);
                        updatedTasks.add(task);
                        break;
                    }
                }
            }
        }
        //更新待办
        if (updatedTasks.size() > 0) {
            syncTodosAssigneeChanged(processInstance, toTaskDto(updatedTasks, false), dozerMapper.map(input, AppPushDto.class), input.getBusinessData());
        }
    }

    //region private method

    /**
     * 流程是否已经结束
     *
     * @param processInstanceId 流程实例ID
     * @return
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
            return list.get(0).getId();
        } else {
            throw new RuntimeException("当前进程实例有多个任务在运行中...");
        }
    }

    private WorkflowTaskDto getWorkflowTaskDto(TaskDefinition task, Map<String, Object> processVariables) {
        WorkflowTaskDto workflowTaskDto = new WorkflowTaskDto();
        workflowTaskDto.setName(task.getNameExpression() != null ?
                getExpressionValue(task.getNameExpression().toString(), processVariables) : null);
        workflowTaskDto.setDescription(task.getDescriptionExpression() != null ?
                getExpressionValue(task.getDescriptionExpression().toString(), processVariables) : null);
        workflowTaskDto.setAssignee(task.getAssigneeExpression() != null ?
                getExpressionValue(task.getAssigneeExpression().toString(), processVariables) : null);
        workflowTaskDto.setOwner(task.getOwnerExpression() != null ?
                getExpressionValue(task.getOwnerExpression().toString(), processVariables) : null);
        workflowTaskDto.setFormKey(task.getFormKeyExpression() != null ?
                getExpressionValue(task.getFormKeyExpression().toString(), processVariables) : null);
        List<WorkflowIdentityLinkDto> identityLinks = new ArrayList<>();

        if (task.getCandidateUserIdExpressions() != null) {
            Set<Expression> candidateUserIdExpressions = task.getCandidateUserIdExpressions();
            for (Expression expression : candidateUserIdExpressions) {
                WorkflowIdentityLinkDto workflowIdentityLinkDto = new WorkflowIdentityLinkDto();
                workflowIdentityLinkDto.setUserId(getExpressionValue(expression.toString(), processVariables));
                workflowIdentityLinkDto.setType(IdentityLinkType.CANDIDATE);
                identityLinks.add(workflowIdentityLinkDto);
            }
        }

        if (task.getCandidateGroupIdExpressions() != null) {
            Set<Expression> candidateGroupIdExpressions = task.getCandidateGroupIdExpressions();
            for (Expression expression : candidateGroupIdExpressions) {
                WorkflowIdentityLinkDto workflowIdentityLinkDto = new WorkflowIdentityLinkDto();
                workflowIdentityLinkDto.setGroupId(getExpressionValue(expression.toString(), processVariables));
                workflowIdentityLinkDto.setType(IdentityLinkType.CANDIDATE);
                identityLinks.add(workflowIdentityLinkDto);
            }
        }
        workflowTaskDto.setIdentityLinks(identityLinks);
        return workflowTaskDto;
    }


    private RelatedNodeInfo getWorkflowNodeDto(TaskDefinition task, Map<String, Object> processVariables) {
        RelatedNodeInfo nodeInfo = new RelatedNodeInfo();
        nodeInfo.setNodeType(ActivitiConstants.NODE_TYPE_USER_TASK);
        nodeInfo.setName(task.getNameExpression() != null ?
                getExpressionValue(task.getNameExpression().toString(), processVariables) : null);
        nodeInfo.setFormKey(task.getFormKeyExpression() != null ?
                getExpressionValue(task.getFormKeyExpression().toString(), processVariables) : null);
        return nodeInfo;
    }

    /**
     * 根据表达式获取值
     *
     * @param expression
     * @param variables
     * @return
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
            ex.printStackTrace();
            return expression;
        }
    }

    /**
     * 获取下一个任务节点
     *
     * @param activityImpl 流程节点信息
     * @param activityId   当前流程节点Id信息
     * @param variables    变量
     * @return
     */
    private TaskDefinition nextTaskDefinition(ActivityImpl activityImpl, String activityId,
                                              Map<String, Object> variables) {

        // 如果遍历节点为用户任务并且节点不是当前节点信息
        if ("userTask".equals(activityImpl.getProperty("type")) && !activityId.equals(activityImpl.getId())) {
            // 获取该节点下一个节点信息
            return ((UserTaskActivityBehavior) activityImpl.getActivityBehavior()).getTaskDefinition();
        } else {
            // 获取节点所有流向线路信息
            List<PvmTransition> outTransitions = activityImpl.getOutgoingTransitions();

            //如果只有一个流向
            if (outTransitions.size() == 1) {
                PvmTransition pvmTransition = outTransitions.get(0);
                PvmActivity destination = pvmTransition.getDestination();// 获取线路的终点节点
                // 如果是userTask,返回下一个节点
                if ("userTask".equals(destination.getProperty("type"))) {
                    return ((UserTaskActivityBehavior) ((ActivityImpl) destination).getActivityBehavior()).getTaskDefinition();
                } else if ("exclusiveGateway".equals(destination.getProperty("type"))) { // 如果是排他网关
                    List<PvmTransition> outTransitionsTemp = destination.getOutgoingTransitions();
                    // 如果排他网关只有一条线路信息
                    if (outTransitionsTemp.size() == 1) {
                        return nextTaskDefinition((ActivityImpl) outTransitionsTemp.get(0).getDestination(),
                                activityId, variables);
                    } else if (outTransitionsTemp.size() > 1) { // 如果排他网关有多条线路信息
                        for (PvmTransition outTransition : outTransitionsTemp) {
                            Object conditionText = outTransition.getProperty("conditionText"); // 获取排他网关线路判断条件信息
                            // 判断el表达式是否成立
                            if (conditionText != null && variables != null && isCondition(StrUtil.trim(conditionText.toString()), variables)) {
                                return nextTaskDefinition((ActivityImpl) outTransition.getDestination(), activityId,
                                        variables);
                            }
                        }
                    }
                }
            } else if (outTransitions.size() > 1) { // 如果有多个流向
                for (PvmTransition outTransition : outTransitions) {
                    Object conditionText = outTransition.getProperty("conditionText");
                    // 判断el表达式是否成立
                    if (conditionText != null && variables != null && isCondition(StrUtil.trim(conditionText.toString()), variables)) {
                        return nextTaskDefinition((ActivityImpl) outTransition.getDestination(), activityId, variables);
                    }
                }
            }
            return null;
        }
    }

    private TaskDefinition getTaskDefinition(ProcessInstance processInstance, Task task) {
        //获取流程发布Id信息
        String definitionId = processInstance.getProcessDefinitionId();
        ProcessDefinitionEntity processDefinitionEntity =
                (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(definitionId);
        //获取流程所有节点信息
        List<ActivityImpl> activitiList = processDefinitionEntity.getActivities();
        //遍历所有节点信息
        for (ActivityImpl activityImpl : activitiList) {
            if ("userTask".equals(activityImpl.getProperty("type"))) {
                TaskDefinition taskDefinition = ((UserTaskActivityBehavior) activityImpl.getActivityBehavior()).getTaskDefinition();
                if (taskDefinition.getKey().equals(task.getTaskDefinitionKey())) {
                    return taskDefinition;
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
            ex.printStackTrace();
        }
        return flag;
    }

    //计算关联节点
    private void caculateLinkTask(List<PvmTransition> outgoingTransitions, List<WorkflowTaskDto> taskList, Map<String
            , Object> processVariables, boolean autoCalculate,
                                  String defaultFlowId) {
        WorkflowTaskDto defaultWorkflow = null;
        // 找到当前节点关联的节点
        for (PvmTransition outgoingTransition : outgoingTransitions) {
            ActivityImpl destination = (ActivityImpl) outgoingTransition.getDestination();
            if ("userTask".equals(destination.getProperty("type"))) {
                TaskDefinition taskDefinition = (TaskDefinition) destination.getProperty("taskDefinition");
                WorkflowTaskDto workflowTaskDto = getWorkflowTaskDto(taskDefinition, processVariables);

                Object flowName = outgoingTransition.getProperty("name");
                //简单定义驳回
                if (flowName != null && flowName.toString().contains(BOHUI)) {
                    workflowTaskDto.setTaskDirection("from");
                    taskList.add(workflowTaskDto);
                } else {
                    if (autoCalculate) {
                        if (outgoingTransition.getId().equals(defaultFlowId)) {
                            workflowTaskDto.setTaskDirection("to");
                            defaultWorkflow = workflowTaskDto;
                        } else {
                            Object conditionText = outgoingTransition.getProperty("conditionText");
                            boolean condition = true;
                            // 判断el表达式是否成立
                            //写死判断不是flag的表达式则跳过
                            if (conditionText != null && processVariables != null) {
                                condition = false;
                                if (isCondition(StrUtil.trim(conditionText.toString()), processVariables)) {
                                    condition = true;
                                }
                            }
                            if (condition) {
                                workflowTaskDto.setTaskDirection("to");
                                taskList.add(workflowTaskDto);
                            }
                        }
                    } else {
                        workflowTaskDto.setTaskDirection("to");
                        taskList.add(workflowTaskDto);
                    }
                }
            }
            // 如果是网关,再往下找一层(仅考虑为userTask的情况),并且默认为
            else if ("parallelGateway".equals(destination.getProperty("type")) || "exclusiveGateway".equals(destination.getProperty("type"))) {
                List<PvmTransition> outTransitionsTemp = destination.getOutgoingTransitions();
                caculateLinkTask(outTransitionsTemp, taskList, processVariables, autoCalculate,
                        (String) destination.getProperty("default"));
            }
        }
        if (autoCalculate && StringUtils.isNotEmpty(defaultFlowId) && defaultWorkflow != null) {
            if (!taskList.stream().anyMatch(t -> t.getTaskDirection().equals("to"))) {
                taskList.add(defaultWorkflow);
            }
        }
        taskList.forEach(e -> e.setProcessVariables(processVariables));
    }


    //计算关联节点
    private void caculateLinkNode(List<PvmTransition> outgoingTransitions, List<RelatedNodeInfo> nodeList, Map<String
            , Object> processVariables, boolean autoCalculate,
                                  String defaultFlowId) {
        RelatedNodeInfo defaultWorkflow = null;
        // 找到当前节点关联的节点
        for (PvmTransition outgoingTransition : outgoingTransitions) {
            ActivityImpl destination = (ActivityImpl) outgoingTransition.getDestination();
            if ("userTask".equals(destination.getProperty("type"))) {
                TaskDefinition taskDefinition = (TaskDefinition) destination.getProperty("taskDefinition");
                RelatedNodeInfo nodeInfo = getWorkflowNodeDto(taskDefinition, processVariables);

                Object flowName = outgoingTransition.getProperty("name");
                //简单定义驳回
                if (flowName != null && flowName.toString().contains(BOHUI)) {
                    nodeInfo.setTaskDirection("from");
                    nodeList.add(nodeInfo);
                } else {
                    if (autoCalculate) {
                        if (outgoingTransition.getId().equals(defaultFlowId)) {
                            nodeInfo.setTaskDirection("to");
                            defaultWorkflow = nodeInfo;
                        } else {
                            Object conditionText = outgoingTransition.getProperty("conditionText");
                            boolean condition = true;
                            // 判断el表达式是否成立
                            //写死判断不是flag的表达式则跳过
                            if (conditionText != null && processVariables != null) {
                                condition = false;
                                if (isCondition(StrUtil.trim(conditionText.toString()), processVariables)) {
                                    condition = true;
                                }
                            }
                            if (condition) {
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
            // 如果是网关,再往下找一层(仅考虑为userTask的情况),并且默认为
            else if ("parallelGateway".equals(destination.getProperty("type")) || "exclusiveGateway".equals(destination.getProperty("type"))) {
                List<PvmTransition> outTransitionsTemp = destination.getOutgoingTransitions();
                caculateLinkNode(outTransitionsTemp, nodeList, processVariables, autoCalculate,
                        (String) destination.getProperty("default"));
            }
            //end event
            else if ("endEvent".equals(destination.getProperty("type"))) {
                RelatedNodeInfo endNode = new RelatedNodeInfo();
                endNode.setNodeType(ActivitiConstants.NODE_TYPE_END_EVENT);
                if (autoCalculate) {
                    Object conditionText = outgoingTransition.getProperty("conditionText");
                    boolean condition = true;
                    // 判断el表达式是否成立
                    //写死判断不是flag的表达式则跳过
                    if (conditionText != null && processVariables != null) {
                        condition = false;
                        if (isCondition(StrUtil.trim(conditionText.toString()), processVariables)) {
                            condition = true;
                        }
                    }
                    if (condition) {
                        nodeList.add(endNode);
                    }
                } else {
                    nodeList.add(endNode);
                }
            }

        }
        if (autoCalculate && StringUtils.isNotEmpty(defaultFlowId) && defaultWorkflow != null) {
            if (!nodeList.stream().anyMatch(t -> t.getTaskDirection().equals("to"))) {
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
            workflowTaskHistoryDto.setComment(taskComments.get(0).getFullMessage());
        }

        // 获取审批的变量
        List<HistoricVariableInstance> historicVariableInstances =
                historyService.createHistoricVariableInstanceQuery().taskId(query.getId()).list();
        Map<String, Object> variables = new HashMap<>();
        for (HistoricVariableInstance historicVariableInstance : historicVariableInstances) {
            variables.put(historicVariableInstance.getVariableName(), historicVariableInstance.getValue());
        }
        workflowTaskHistoryDto.setVariables(variables);
        workflowTaskHistoryDto.setBeginTime(DateUtils.toLocalDateTime(query.getStartTime()));
        workflowTaskHistoryDto.setStopTime(DateUtils.toLocalDateTime(query.getEndTime()));
        return workflowTaskHistoryDto;
    }


    /**
     * 判断userTask是否有权限
     *
     * @param task
     * @return
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
     *
     * @param formKey
     * @return
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
            List<String> assigneeList = Arrays.stream(assignees).map(String::trim).collect(toList());
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
        if (assignees != null && assignees.size() > 0) {
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
        workflowTaskDto.setFormProperties(DozerUtils.mapList(dozerMapper, formProperties, FormPropertyDto.class));
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
            variables.put(ActivitiConstants.TITLE, workflowProcessDto.getTitle());
            variables.put(ActivitiConstants.TYPE_NAME, workflowProcessDto.getTypeName());
            variables.put(ActivitiConstants.BUSINESS_TYPE, workflowProcessDto.getType());
            variables.put(ActivitiConstants.FILTER_STATION, workflowProcessDto.isFilterStation());
            //填充发起人
            if (StrUtil.isEmpty(workflowProcessDto.getStarter()) && LoginUserIdContextHolder.getUserId() != null) {
                UserFullListDto currUser = userRpcService.getUserFullById(LoginUserIdContextHolder.getUserId());
                variables.put(ActivitiConstants.STARTER, currUser.getName());
            } else {
                variables.put(ActivitiConstants.STARTER, workflowProcessDto.getStarter());
            }

            ProcessInstance processInstance = runtimeService
                    .startProcessInstanceByKeyAndTenantId(workflowProcessDto.getProcessDefinitionKey(),
                            workflowProcessDto.getBusinessKey(), variables,
                            String.valueOf(TenantContextHolder.getTenantId()));
            return processInstance;
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
                String title = variables.get(ActivitiConstants.TITLE).toString();
                String typeName = variables.get(ActivitiConstants.TYPE_NAME).toString();
                String type = ActivitiConstants.TYPE;
                String state = p.getName();
                String businessType = variables.get(ActivitiConstants.BUSINESS_TYPE).toString();
                boolean filterStation = (Boolean) variables.get(ActivitiConstants.FILTER_STATION);
                TaskFormKey taskFormKey = getTaskFormKey(p.getFormKey());
                boolean addTodo = taskFormKey.getActiviti().getNotice().isAddTodo();
                boolean appPush = taskFormKey.getActiviti().getNotice().isAppPush();
                String starter = (String) variables.get(ActivitiConstants.STARTER);
                String businessId = processInstance.getId() + "|" + p.getId();
                ToDoTargetType toDoTargetType = ToDoTargetType.Role;
                if (p.getAssigneeType().equals(AssigneeTypeEnum.UserId)) {
                    toDoTargetType = ToDoTargetType.UserId;
                }

                TodoExtensionsDto extensionsDto = new TodoExtensionsDto();
                extensionsDto.setBusinessId(processInstance.getBusinessKey());
                extensionsDto.setType(businessType);
                extensionsDto.setBusinessData(businessData);
                String extensions = "";
                try {
                    extensions = mapper.writeValueAsString(extensionsDto);
                } catch (JsonProcessingException e) {
                    log.error("序列化对象异常", e);
                }
                if (StrUtil.isNotEmpty(p.getAssignee()) && addTodo) {
                    String[] assignees = StrUtil.split(p.getAssignee(), ",");
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
            log.error("formKey配置有误！formKey=" + formKeyStr);
            return new TaskFormKey();
        }
    }

    //跳转到指定的userTask节点
    private void gotoAssignActivity(Task task, ActivityImpl currActivity, ActivityImpl gotoActivity, String message) {
        //获取当前审批任务的出线
        List<PvmTransition> currOutgoingTransitions = currActivity.getOutgoingTransitions();
        List<PvmTransition> oldTransitions = new ArrayList<>(currOutgoingTransitions);
        currOutgoingTransitions.clear();

        TransitionImpl newTransition = currActivity.createOutgoingTransition();
        newTransition.setDestination(gotoActivity);

        CompleteTaskInputDto dto = new CompleteTaskInputDto();
        dto.setTaskId(task.getId());
        dto.setProcessInstanceId(task.getProcessInstanceId());
        dto.setComment(message);
        completeTask(dto, false);


        //恢复原来的出线
        currActivity.getOutgoingTransitions().remove(newTransition);
        currOutgoingTransitions.addAll(oldTransitions);
    }

    private boolean checkProcessCanRecallPre(List<HistoricTaskInstance> historicTaskInstances, ProcessDefinitionEntity processDefinitionEntity, List<Task> userTasks) {
        if (historicTaskInstances.size() > 0) {
            //上一个流程实例
            HistoricTaskInstance historicTaskInstance = historicTaskInstances.get(0);
            String nowUserId = LoginUserIdContextHolder.getUserId() == null ? "" : LoginUserIdContextHolder.getUserId().toString();
            if (!historicTaskInstance.getAssignee().equals(nowUserId)) {
                return false;
            }
            //上一个流程id描述
            ActivityImpl activity = processDefinitionEntity.findActivity(historicTaskInstance.getTaskDefinitionKey());
            //判断上一节点是会签则无法撤回
            Object multiInstance = activity.getProperty("multiInstance");
            if (multiInstance != null) {
                return false;
            }
            if (userTasks.size() == 1) {
                Task currTask = userTasks.get(0);
                TaskFormKey taskFormKey = getTaskFormKey(currTask.getFormKey());
                if (!taskFormKey.getActiviti().isCallBackPre()) {
                    return false;
                }
                //当前节点审批过，并且撤回的节点是自己则不允许撤回
                if (currTask.getTaskDefinitionKey().equals(historicTaskInstance.getTaskDefinitionKey())) {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    private List<HistoricTaskInstance> getHistoricTaskInstanceDesc(String processInstanceId) {
        return historyService.createHistoricTaskInstanceQuery().processUnfinished().processInstanceId(processInstanceId).finished().orderByTaskCreateTime().desc().list();
    }

    private ProcessDefinitionEntity getProcessDefinitionEntity(String processInstanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        ProcessDefinitionEntity processDefinitionEntity = (ProcessDefinitionEntity) repositoryService.getProcessDefinition(processInstance.getProcessDefinitionId());
        return processDefinitionEntity;
    }


    //endregion
}
