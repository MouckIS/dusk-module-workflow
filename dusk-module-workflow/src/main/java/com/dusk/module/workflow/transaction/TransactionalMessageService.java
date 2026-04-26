package com.dusk.module.workflow.transaction;

import com.dusk.common.core.tenant.TenantContextHolder;
import com.dusk.module.workflow.core.config.WorkflowMqConfig;
import com.dusk.workflow.dto.WorkflowEventDto;
import com.dusk.workflow.trace.WorkflowTraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 事务消息服务
 * <p>
 * 实现本地消息表模式，保证工作流事件消息的最终一致性。
 * </p>
 * <p>
 * 工作流程：
 * <ol>
 *   <li>业务操作时，将消息保存到本地消息表（同一事务）</li>
 *   <li>事务提交后，定时任务扫描并发送待处理消息</li>
 *   <li>发送失败时，按指数退避策略重试</li>
 *   <li>超过最大重试次数后，标记为失败，人工介入</li>
 * </ol>
 * </p>
 *
 * @author kefuming
 */
@Slf4j
@Service
public class TransactionalMessageService {

    @Autowired
    private TransactionalMessageMapper messageMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    /**
     * 保存消息到本地表（必须在已有事务中调用）
     * <p>
     * 使用 {@code Propagation.MANDATORY} 确保在事务上下文中执行，
     * 保证消息与业务操作的原子性。
     * </p>
     *
     * @param event 工作流事件
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveMessage(WorkflowEventDto event) {
        try {
            TransactionalMessage message = new TransactionalMessage();
            message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
            message.setEventType(event.getEventType().name());
            message.setProcessInstanceId(event.getProcessInstanceId());
            message.setProcessDefinitionKey(event.getProcessDefinitionKey());
            message.setBusinessKey(event.getBusinessKey());
            message.setPayload(objectMapper.writeValueAsString(event));
            message.setStatus(TransactionalMessage.STATUS_PENDING);
            message.setRetryCount(0);
            message.setMaxRetry(5);
            message.setNextRetryTime(LocalDateTime.now());
            message.setTraceId(WorkflowTraceContext.getTraceIdOrNull());
            message.setCreatedAt(LocalDateTime.now());
            message.setUpdatedAt(LocalDateTime.now());
            
            // 获取租户ID
            Long tenantId = TenantContextHolder.getTenantId();
            message.setTenantId(tenantId);

            messageMapper.save(message);
            log.debug("事务消息已保存: messageId={}, eventType={}, processInstanceId={}",
                    message.getMessageId(), message.getEventType(), message.getProcessInstanceId());
        } catch (Exception e) {
            log.error("保存事务消息失败", e);
            throw new RuntimeException("保存事务消息失败", e);
        }
    }

    /**
     * 发送待处理的消息（由定时任务调用）
     *
     * @param batchSize 每批处理的消息数量
     * @return 成功发送的消息数量
     */
    @Transactional
    public int sendPendingMessages(int batchSize) {
        if (rabbitTemplate == null) {
            log.debug("RabbitTemplate未配置，跳过消息发送");
            return 0;
        }

        List<TransactionalMessage> messages = messageMapper.selectPendingMessages(batchSize);
        if (messages.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (TransactionalMessage msg : messages) {
            try {
                sendToMq(msg);
                messageMapper.updateStatusToSent(msg.getId());
                successCount++;
                log.debug("消息发送成功: messageId={}, eventType={}",
                        msg.getMessageId(), msg.getEventType());
            } catch (Exception e) {
                handleSendFailure(msg, e);
            }
        }

        log.info("消息发送完成: 总数={}, 成功={}, 失败={}",
                messages.size(), successCount, messages.size() - successCount);
        return successCount;
    }

    /**
     * 发送消息到 MQ
     */
    private void sendToMq(TransactionalMessage msg) {
        String routingKey = "workflow.event." + msg.getEventType().toLowerCase();
        rabbitTemplate.convertAndSend(
                WorkflowMqConfig.WORKFLOW_EVENT_EXCHANGE,
                routingKey,
                msg.getPayload()
        );
    }

    /**
     * 处理发送失败
     */
    private void handleSendFailure(TransactionalMessage msg, Exception e) {
        String errorMessage = e.getMessage();
        if (errorMessage != null && errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 500);
        }

        // 计算下次重试时间（指数退避）
        int retryCount = msg.getRetryCount() + 1;
        Duration delay = calculateRetryDelay(retryCount);
        LocalDateTime nextRetryTime = LocalDateTime.now().plus(delay);

        messageMapper.updateRetry(msg.getId(), errorMessage, nextRetryTime);

        if (retryCount >= msg.getMaxRetry()) {
            log.error("消息发送最终失败，已达最大重试次数: messageId={}, eventType={}, retryCount={}",
                    msg.getMessageId(), msg.getEventType(), retryCount, e);
        } else {
            log.warn("消息发送失败，将在 {} 后重试: messageId={}, eventType={}, retryCount={}",
                    delay, msg.getMessageId(), msg.getEventType(), retryCount);
        }
    }

    /**
     * 计算重试延迟（指数退避）
     * 1次: 5秒, 2次: 10秒, 3次: 20秒, 4次: 40秒, 5次: 80秒
     */
    private Duration calculateRetryDelay(int retryCount) {
        long seconds = (long) (5 * Math.pow(2, retryCount - 1));
        return Duration.ofSeconds(Math.min(seconds, 300)); // 最大5分钟
    }

    /**
     * 删除已发送的历史消息
     *
     * @param retentionDuration 保留时长
     * @return 删除的消息数量
     */
    @Transactional
    public int deleteOldSentMessages(Duration retentionDuration) {
        LocalDateTime beforeTime = LocalDateTime.now().minus(retentionDuration);
        int deleted = messageMapper.deleteOldSentMessages(beforeTime);
        if (deleted > 0) {
            log.info("清理历史消息: 删除 {} 条 {} 之前的已发送消息", deleted, beforeTime);
        }
        return deleted;
    }

    /**
     * 获取消息统计信息
     */
    public List<TransactionalMessageMapper.MessageStatusCount> getMessageStats() {
        return messageMapper.countByStatus();
    }
}
