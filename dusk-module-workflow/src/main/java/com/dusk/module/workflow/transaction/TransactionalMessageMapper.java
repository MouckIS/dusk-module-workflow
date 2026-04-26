package com.dusk.module.workflow.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事务消息 Mapper
 *
 * @author kefuming
 */
@Repository
public interface TransactionalMessageMapper extends JpaRepository<TransactionalMessage, Long> {

    /**
     * 查询待发送的消息
     *
     * @param limit 最大条数
     * @return 待发送消息列表
     */
    @Query(value = "SELECT * FROM workflow_transactional_message " +
            "WHERE status = 'PENDING' " +
            "AND (next_retry_time IS NULL OR next_retry_time <= NOW()) " +
            "ORDER BY created_at ASC " +
            "LIMIT :limit", nativeQuery = true)
    List<TransactionalMessage> selectPendingMessages(@Param("limit") int limit);

    /**
     * 更新消息状态为已发送
     *
     * @param id 消息ID
     * @return 影响行数
     */
    @Modifying
    @Query(value = "UPDATE workflow_transactional_message " +
            "SET status = 'SENT', updated_at = NOW() " +
            "WHERE id = :id", nativeQuery = true)
    int updateStatusToSent(@Param("id") Long id);

    /**
     * 更新消息状态为失败并设置重试
     *
     * @param id            消息ID
     * @param errorMessage  错误信息
     * @param nextRetryTime 下次重试时间
     * @return 影响行数
     */
    @Modifying
    @Query(value = "UPDATE workflow_transactional_message " +
            "SET retry_count = retry_count + 1, " +
            "error_message = :errorMessage, " +
            "next_retry_time = :nextRetryTime, " +
            "status = CASE WHEN retry_count >= max_retry THEN 'FAILED' ELSE 'PENDING' END, " +
            "updated_at = NOW() " +
            "WHERE id = :id", nativeQuery = true)
    int updateRetry(@Param("id") Long id,
                    @Param("errorMessage") String errorMessage,
                    @Param("nextRetryTime") LocalDateTime nextRetryTime);

    /**
     * 删除已发送的历史消息
     *
     * @param beforeTime 时间界限
     * @return 删除行数
     */
    @Modifying
    @Query(value = "DELETE FROM workflow_transactional_message " +
            "WHERE status = 'SENT' AND created_at < :beforeTime", nativeQuery = true)
    int deleteOldSentMessages(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 统计各状态的消息数量
     *
     * @return 统计结果
     */
    @Query(value = "SELECT status as status, COUNT(*) as count FROM workflow_transactional_message GROUP BY status", nativeQuery = true)
    List<MessageStatusCount> countByStatus();

    /**
     * 状态统计结果
     */
    interface MessageStatusCount {
        String getStatus();
        Long getCount();
    }
}
