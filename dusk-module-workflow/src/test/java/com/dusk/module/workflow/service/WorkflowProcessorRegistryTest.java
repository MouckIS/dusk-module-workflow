package com.dusk.module.workflow.service;

import com.dusk.workflow.dto.CompleteTaskInputDto;
import com.dusk.workflow.dto.StartProcessOutDto;
import com.dusk.workflow.dto.WorkflowProcessDto;
import com.dusk.workflow.dto.WorkflowTaskDto;
import com.dusk.workflow.service.IWorkflowApprovalProcessor;
import com.dusk.workflow.service.IWorkflowSubmitProcessor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkflowProcessorRegistry 单元测试
 * 100%分支覆盖：null列表、空列表、正常列表、重复processKey（合并策略）、查找存在/不存在的key
 */
class WorkflowProcessorRegistryTest {

    @Test
    void constructor_withNullLists_shouldCreateEmptyMaps() {
        WorkflowProcessorRegistry registry = new WorkflowProcessorRegistry(null, null);
        assertNull(registry.getSubmitProcessor("any"));
        assertNull(registry.getApprovalProcessor("any"));
    }

    @Test
    void constructor_withEmptyLists_shouldCreateEmptyMaps() {
        WorkflowProcessorRegistry registry = new WorkflowProcessorRegistry(
                Collections.emptyList(), Collections.emptyList());
        assertNull(registry.getSubmitProcessor("any"));
        assertNull(registry.getApprovalProcessor("any"));
    }

    @Test
    void constructor_withNormalLists_shouldRegisterProcessors() {
        IWorkflowSubmitProcessor submitProcessor = new TestSubmitProcessor("key1");
        IWorkflowApprovalProcessor approvalProcessor = new TestApprovalProcessor("key2");

        WorkflowProcessorRegistry registry = new WorkflowProcessorRegistry(
                List.of(submitProcessor), List.of(approvalProcessor));

        assertSame(submitProcessor, registry.getSubmitProcessor("key1"));
        assertSame(approvalProcessor, registry.getApprovalProcessor("key2"));
    }

    @Test
    void constructor_withDuplicateProcessKey_shouldKeepLastOne() {
        IWorkflowSubmitProcessor first = new TestSubmitProcessor("dup");
        IWorkflowSubmitProcessor second = new TestSubmitProcessor("dup");
        IWorkflowApprovalProcessor firstA = new TestApprovalProcessor("dup");
        IWorkflowApprovalProcessor secondA = new TestApprovalProcessor("dup");

        WorkflowProcessorRegistry registry = new WorkflowProcessorRegistry(
                Arrays.asList(first, second), Arrays.asList(firstA, secondA));

        // 合并策略 (a, b) -> b ，后者覆盖前者
        assertSame(second, registry.getSubmitProcessor("dup"));
        assertSame(secondA, registry.getApprovalProcessor("dup"));
    }

    @Test
    void getSubmitProcessor_withNonExistentKey_shouldReturnNull() {
        WorkflowProcessorRegistry registry = new WorkflowProcessorRegistry(
                List.of(new TestSubmitProcessor("exists")), Collections.emptyList());
        assertNull(registry.getSubmitProcessor("notExists"));
    }

    @Test
    void getApprovalProcessor_withNonExistentKey_shouldReturnNull() {
        WorkflowProcessorRegistry registry = new WorkflowProcessorRegistry(
                Collections.emptyList(), List.of(new TestApprovalProcessor("exists")));
        assertNull(registry.getApprovalProcessor("notExists"));
    }

    // ---- 辅助测试实现类 ----
    private static class TestSubmitProcessor implements IWorkflowSubmitProcessor {
        private final String processKey;

        TestSubmitProcessor(String processKey) {
            this.processKey = processKey;
        }

        @Override
        public String getProcessKey() {
            return processKey;
        }

        @Override
        public void preSubmit(WorkflowProcessDto input) {}

        @Override
        public void postSubmit(StartProcessOutDto output, WorkflowProcessDto input) {}
    }

    private static class TestApprovalProcessor implements IWorkflowApprovalProcessor {
        private final String processKey;

        TestApprovalProcessor(String processKey) {
            this.processKey = processKey;
        }

        @Override
        public String getProcessKey() {
            return processKey;
        }

        @Override
        public void preApproval(CompleteTaskInputDto input) {}

        @Override
        public void postApproval(List<WorkflowTaskDto> result, CompleteTaskInputDto input) {}
    }
}

