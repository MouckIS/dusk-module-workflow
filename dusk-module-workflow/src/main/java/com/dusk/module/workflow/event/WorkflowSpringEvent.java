package com.dusk.module.workflow.event;

import com.dusk.workflow.dto.WorkflowEventDto;
import org.springframework.context.ApplicationEvent;

/**
 * Spring内部事件包装，用于进程内事件传递
 * <p>
 * 将 {@link WorkflowEventDto} 包装为 Spring {@link ApplicationEvent}，
 * 由 {@code WorkflowEventPublisher} 发布，{@code WorkflowEventConsumer} 通过 {@code @EventListener} 接收。
 * </p>
 * <p>
 * 这是双通道发布策略的进程内通道，保证即使 RabbitMQ 未启用，
 * 本地注册的 {@link com.dusk.workflow.service.IWorkflowListener} 仍能收到事件通知。
 * </p>
 *
 * @author kefuming
 * @see WorkflowEventPublisher
 * @see WorkflowEventConsumer
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

