package com.dusk.module.workflow.event;

import com.dusk.workflow.dto.WorkflowEventDto;
import org.springframework.context.ApplicationEvent;

/**
 * Spring内部事件包装，用于进程内事件传递
 *
 * @author kefuming
 * @date 2026-02-28
 */
public class WorkflowSpringEvent extends ApplicationEvent {

    private final WorkflowEventDto workflowEvent;

    public WorkflowSpringEvent(Object source, WorkflowEventDto workflowEvent) {
        super(source);
        this.workflowEvent = workflowEvent;
    }

    public WorkflowEventDto getWorkflowEvent() {
        return workflowEvent;
    }
}

