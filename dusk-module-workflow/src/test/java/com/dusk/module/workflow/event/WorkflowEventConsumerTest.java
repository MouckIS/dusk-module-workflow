package com.dusk.module.workflow.event;

import cn.hutool.core.util.StrUtil;
import com.dusk.workflow.dto.WorkflowEventDto;
import com.dusk.workflow.enums.WorkflowEventType;
import com.dusk.workflow.service.IWorkflowListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * WorkflowEventConsumer 单元测试
 * 覆盖：handleSpringEvent、handleMqEvent（正常/JSON异常）、dispatch分发策略（processKey为null、匹配、不匹配、listener异常）
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEventConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WorkflowEventConsumer consumer;

    @Test
    void handleSpringEvent_shouldDispatch() {
        IWorkflowListener listener = mock(IWorkflowListener.class);
        when(listener.getProcessKey()).thenReturn(null); // 监听所有
        setListeners(List.of(listener));

        WorkflowEventDto event = new WorkflowEventDto();
        event.setEventType(WorkflowEventType.TASK_COMPLETED);
        event.setProcessDefinitionKey("testKey");

        consumer.handleSpringEvent(new WorkflowSpringEvent(this, event));

        verify(listener).onWorkflowEvent(event);
    }

    @Test
    void handleMqEvent_validJson_shouldDispatch() throws Exception {
        IWorkflowListener listener = mock(IWorkflowListener.class);
        when(listener.getProcessKey()).thenReturn(null);
        setListeners(List.of(listener));

        WorkflowEventDto event = new WorkflowEventDto();
        event.setEventType(WorkflowEventType.PROCESS_STARTED);
        when(objectMapper.readValue(eq("{\"json\"}"), eq(WorkflowEventDto.class))).thenReturn(event);

        consumer.handleMqEvent("{\"json\"}");

        verify(listener).onWorkflowEvent(event);
    }

    @Test
    void handleMqEvent_invalidJson_shouldNotThrow() throws Exception {
        when(objectMapper.readValue(anyString(), eq(WorkflowEventDto.class)))
                .thenThrow(new RuntimeException("parse error"));

        // 不抛异常
        consumer.handleMqEvent("invalid json");
    }

    @Test
    void dispatch_listenerWithNullProcessKey_shouldReceiveAllEvents() {
        IWorkflowListener listener = mock(IWorkflowListener.class);
        when(listener.getProcessKey()).thenReturn(null);
        setListeners(List.of(listener));

        WorkflowEventDto event = new WorkflowEventDto();
        event.setProcessDefinitionKey("anyKey");
        event.setEventType(WorkflowEventType.TASK_CREATED);

        consumer.handleSpringEvent(new WorkflowSpringEvent(this, event));

        verify(listener).onWorkflowEvent(event);
    }

    @Test
    void dispatch_listenerWithMatchingProcessKey_shouldReceiveEvent() {
        IWorkflowListener listener = mock(IWorkflowListener.class);
        when(listener.getProcessKey()).thenReturn("matchKey");
        setListeners(List.of(listener));

        WorkflowEventDto event = new WorkflowEventDto();
        event.setProcessDefinitionKey("matchKey");
        event.setEventType(WorkflowEventType.TASK_COMPLETED);

        consumer.handleSpringEvent(new WorkflowSpringEvent(this, event));

        verify(listener).onWorkflowEvent(event);
    }

    @Test
    void dispatch_listenerWithMismatchedProcessKey_shouldNotReceiveEvent() {
        IWorkflowListener listener = mock(IWorkflowListener.class);
        when(listener.getProcessKey()).thenReturn("otherKey");
        setListeners(List.of(listener));

        WorkflowEventDto event = new WorkflowEventDto();
        event.setProcessDefinitionKey("matchKey");
        event.setEventType(WorkflowEventType.TASK_COMPLETED);

        consumer.handleSpringEvent(new WorkflowSpringEvent(this, event));

        verify(listener, never()).onWorkflowEvent(any());
    }

    @Test
    void dispatch_listenerThrowsException_shouldNotBlockOtherListeners() {
        IWorkflowListener failingListener = mock(IWorkflowListener.class);
        when(failingListener.getProcessKey()).thenReturn(null);
        doThrow(new RuntimeException("boom")).when(failingListener).onWorkflowEvent(any());

        IWorkflowListener successListener = mock(IWorkflowListener.class);
        when(successListener.getProcessKey()).thenReturn(null);

        setListeners(Arrays.asList(failingListener, successListener));

        WorkflowEventDto event = new WorkflowEventDto();
        event.setEventType(WorkflowEventType.TASK_COMPLETED);

        consumer.handleSpringEvent(new WorkflowSpringEvent(this, event));

        verify(failingListener).onWorkflowEvent(event);
        verify(successListener).onWorkflowEvent(event);
    }

    @Test
    void dispatch_noListeners_shouldNotThrow() {
        setListeners(Collections.emptyList());
        WorkflowEventDto event = new WorkflowEventDto();
        event.setEventType(WorkflowEventType.PROCESS_COMPLETED);

        consumer.handleSpringEvent(new WorkflowSpringEvent(this, event));
    }

    private void setListeners(List<IWorkflowListener> listeners) {
        try {
            var field = WorkflowEventConsumer.class.getDeclaredField("listeners");
            field.setAccessible(true);
            field.set(consumer, listeners);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

