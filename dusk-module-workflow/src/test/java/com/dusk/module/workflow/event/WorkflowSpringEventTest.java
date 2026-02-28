package com.dusk.module.workflow.event;

import com.dusk.workflow.dto.WorkflowEventDto;
import com.dusk.workflow.enums.WorkflowEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkflowSpringEvent 单元测试
 */
class WorkflowSpringEventTest {

    @Test
    void constructor_shouldStoreWorkflowEvent() {
        WorkflowEventDto dto = new WorkflowEventDto();
        dto.setEventType(WorkflowEventType.TASK_COMPLETED);
        dto.setProcessInstanceId("proc1");

        WorkflowSpringEvent event = new WorkflowSpringEvent(this, dto);

        assertSame(dto, event.getWorkflowEvent());
        assertEquals(WorkflowEventType.TASK_COMPLETED, event.getWorkflowEvent().getEventType());
        assertEquals("proc1", event.getWorkflowEvent().getProcessInstanceId());
        assertSame(this, event.getSource());
    }
}

