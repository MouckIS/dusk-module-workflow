package com.dusk.module.workflow.event;

import cn.hutool.core.util.StrUtil;
import com.dusk.module.workflow.core.config.WorkflowMqConfig;
import com.dusk.workflow.dto.WorkflowEventDto;
import com.dusk.workflow.service.IWorkflowListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 工作流事件消费者
 * <p>
 * 负责接收MQ消息和Spring内部事件，并分发给所有注册的 {@link IWorkflowListener} 实现
 * </p>
 *
 * @author kefuming
 * @date 2026-02-28
 */
@Slf4j
@Component
public class WorkflowEventConsumer {

    @Autowired(required = false)
    private List<IWorkflowListener> listeners = Collections.emptyList();

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 监听Spring内部事件
     */
    @Async
    @EventListener
    public void handleSpringEvent(WorkflowSpringEvent springEvent) {
        dispatch(springEvent.getWorkflowEvent());
    }

    /**
     * 监听RabbitMQ消息
     */
    @RabbitListener(queues = WorkflowMqConfig.WORKFLOW_EVENT_QUEUE, autoStartup = "${spring.rabbitmq.is-enabled:false}")
    public void handleMqEvent(String message) {
        try {
            WorkflowEventDto event = objectMapper.readValue(message, WorkflowEventDto.class);
            dispatch(event);
        } catch (Exception e) {
            log.error("解析工作流MQ事件失败: {}", message, e);
        }
    }

    /**
     * 分发事件到所有匹配的监听器
     */
    private void dispatch(WorkflowEventDto event) {
        for (IWorkflowListener listener : listeners) {
            try {
                String processKey = listener.getProcessKey();
                // processKey为null表示监听所有流程，否则只监听指定流程
                if (processKey == null || StrUtil.equals(processKey, event.getProcessDefinitionKey())) {
                    listener.onWorkflowEvent(event);
                }
            } catch (Exception e) {
                log.error("工作流事件监听器执行异常: listener={}, eventType={}",
                        listener.getClass().getSimpleName(), event.getEventType(), e);
            }
        }
    }
}

