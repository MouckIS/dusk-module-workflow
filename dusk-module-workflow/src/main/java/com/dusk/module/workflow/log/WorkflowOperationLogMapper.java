package com.dusk.module.workflow.log;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 工作流操作日志 Mapper
 *
 * @author kefuming
 */
@Repository
public interface WorkflowOperationLogMapper extends JpaRepository<WorkflowOperationLog, Long> {
}
