package com.dusk.module.workflow.event;

import com.dusk.common.core.auth.authentication.LoginUserIdContextHolder;
import com.dusk.module.workflow.core.config.WorkflowMqConfig;
import com.dusk.workflow.dto.WorkflowEventDto;
import com.dusk.workflow.enums.WorkflowEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WorkflowEventPublisher 单元测试
 * 覆盖：全参发布、简化版发布、有/无RabbitTemplate、MQ发布异常、LoginUserIdContextHolder有/无值
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WorkflowEventPublisher publisher;

    private MockedStatic<LoginUserIdContextHolder> loginMock;

    @BeforeEach
    void setUp() {
        loginMock = mockStatic(LoginUserIdContextHolder.class);
    }

    @AfterEach
    void tearDown() {
        loginMock.close();
    }

    @Test
    void publish_full_withRabbitTemplate_shouldPublishToSpringAndMq() throws Exception {
        // 注入RabbitTemplate
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        setField(publisher, "rabbitTemplate", rabbitTemplate);

        loginMock.when(LoginUserIdContextHolder::getUserId).thenReturn(100L);
        when(objectMapper.writeValueAsString(any(WorkflowEventDto.class))).thenReturn("{\"eventType\":\"TASK_COMPLETED\"}");

        Map<String, Object> vars = Map.of("key", "val");
        Map<String, Object> biz = Map.of("bizKey", "bizVal");
        publisher.publish(WorkflowEventType.TASK_COMPLETED, "proc1", "defKey", "bk1",
                "task1", "审批", "taskDef1", "assignee1", "同意", vars, biz);

        // 验证Spring事件
        ArgumentCaptor<WorkflowSpringEvent> captor = ArgumentCaptor.forClass(WorkflowSpringEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        WorkflowEventDto event = captor.getValue().getWorkflowEvent();
        assertEquals(WorkflowEventType.TASK_COMPLETED, event.getEventType());
        assertEquals("proc1", event.getProcessInstanceId());
        assertEquals("defKey", event.getProcessDefinitionKey());
        assertEquals("bk1", event.getBusinessKey());
        assertEquals("task1", event.getTaskId());
        assertEquals("审批", event.getTaskName());
        assertEquals("taskDef1", event.getTaskDefinitionKey());
        assertEquals("assignee1", event.getAssignee());
        assertEquals("同意", event.getComment());
        assertEquals("100", event.getOperator());
        assertNotNull(event.getTimestamp());

        // 验证MQ发布
        verify(rabbitTemplate).convertAndSend(
                eq(WorkflowMqConfig.WORKFLOW_EVENT_EXCHANGE),
                eq("workflow.event.task_completed"),
                eq("{\"eventType\":\"TASK_COMPLETED\"}"));
    }

    @Test
    void publish_simplified_shouldDelegateFull() {
        loginMock.when(LoginUserIdContextHolder::getUserId).thenReturn(null);

        publisher.publish(WorkflowEventType.PROCESS_STARTED, "proc1", "defKey", "bk1");

        ArgumentCaptor<WorkflowSpringEvent> captor = ArgumentCaptor.forClass(WorkflowSpringEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        WorkflowEventDto event = captor.getValue().getWorkflowEvent();
        assertEquals(WorkflowEventType.PROCESS_STARTED, event.getEventType());
        assertNull(event.getOperator());
        assertNull(event.getTaskId());
    }

    @Test
    void publish_withoutRabbitTemplate_shouldSkipMq() {
        // rabbitTemplate默认为null（@Autowired(required=false)）
        loginMock.when(LoginUserIdContextHolder::getUserId).thenReturn(null);

        publisher.publish(WorkflowEventType.PROCESS_COMPLETED, "proc1", "key", "bk1");

        // Spring事件仍发布
        verify(applicationEventPublisher).publishEvent(any(WorkflowSpringEvent.class));
        // 没有MQ交互（rabbitTemplate为null）
    }

    @Test
    void publish_mqThrowsException_shouldNotThrow() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        setField(publisher, "rabbitTemplate", rabbitTemplate);

        loginMock.when(LoginUserIdContextHolder::getUserId).thenReturn(1L);
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("serialize error"));

        // 不抛异常
        assertDoesNotThrow(() ->
                publisher.publish(WorkflowEventType.TASK_CREATED, "proc1", "key", "bk1"));

        verify(applicationEventPublisher).publishEvent(any(WorkflowSpringEvent.class));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

