package com.dusk.module.workflow.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 消息补偿定时任务
 * <p>
 * 定期扫描待发送的事务消息，保证消息最终发送成功。
 * </p>
 *
 * @author kefuming
 */
@Slf4j
@Component
public class MessageCompensationTask {

    @Autowired
    private TransactionalMessageService messageService;

    /**
     * 每批处理的消息数量
     */
    @Value("${workflow.message.batch-size:100}")
    private int batchSize;

    /**
     * 历史消息保留天数
     */
    @Value("${workflow.message.retention-days:7}")
    private int retentionDays;

    /**
     * 是否启用补偿任务
     */
    @Value("${workflow.message.compensation.enabled:true}")
    private boolean enabled;

    /**
     * 发送待处理消息
     * 每5秒执行一次
     */
    @Scheduled(fixedDelayString = "${workflow.message.compensation.interval:5000}")
    public void sendPendingMessages() {
        if (!enabled) {
            return;
        }

        try {
            int sent = messageService.sendPendingMessages(batchSize);
            if (sent > 0) {
                log.debug("消息补偿任务: 发送 {} 条消息", sent);
            }
        } catch (Exception e) {
            log.error("消息补偿任务执行失败", e);
        }
    }

    /**
     * 清理已发送的历史消息
     * 每小时执行一次
     */
    @Scheduled(cron = "${workflow.message.cleanup.cron:0 0 * * * ?}")
    public void cleanupSentMessages() {
        if (!enabled) {
            return;
        }

        try {
            int deleted = messageService.deleteOldSentMessages(Duration.ofDays(retentionDays));
            if (deleted > 0) {
                log.info("历史消息清理任务: 删除 {} 条消息", deleted);
            }
        } catch (Exception e) {
            log.error("历史消息清理任务执行失败", e);
        }
    }

    /**
     * 输出消息统计信息
     * 每10分钟执行一次
     */
    @Scheduled(cron = "${workflow.message.stats.cron:0 */10 * * * ?}")
    public void logMessageStats() {
        if (!enabled) {
            return;
        }

        try {
            var stats = messageService.getMessageStats();
            if (!stats.isEmpty()) {
                StringBuilder sb = new StringBuilder("消息统计: ");
                for (var stat : stats) {
                    sb.append(stat.getStatus()).append("=").append(stat.getCount()).append(" ");
                }
                log.info(sb.toString().trim());
            }
        } catch (Exception e) {
            log.warn("获取消息统计失败", e);
        }
    }
}
