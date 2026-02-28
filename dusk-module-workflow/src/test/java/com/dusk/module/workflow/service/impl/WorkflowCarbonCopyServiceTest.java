package com.dusk.module.workflow.service.impl;

import com.dusk.module.workflow.event.WorkflowEventPublisher;
import com.dusk.workflow.dto.CarbonCopyInput;
import com.dusk.workflow.enums.WorkflowEventType;
import com.dusk.workflow.service.INotificationRpcService;
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
 * WorkflowCarbonCopyService 单元测试
 * 覆盖全部分支：null input、null/empty ccUserIds、正常发送、rpc异常、字符串拼接入口、空白字符串
 */
@ExtendWith(MockitoExtension.class)
class WorkflowCarbonCopyServiceTest {

    @Mock
    private INotificationRpcService notificationRpcService;

    @Mock
    private WorkflowEventPublisher eventPublisher;

    @InjectMocks
    private WorkflowCarbonCopyService carbonCopyService;

    // ==================== sendCarbonCopy(CarbonCopyInput) ====================

    @Test
    void sendCarbonCopy_inputNull_shouldReturn() {
        carbonCopyService.sendCarbonCopy((CarbonCopyInput) null);
        verifyNoInteractions(notificationRpcService, eventPublisher);
    }

    @Test
    void sendCarbonCopy_ccUserIdsNull_shouldReturn() {
        CarbonCopyInput input = new CarbonCopyInput();
        input.setCcUserIds(null);
        carbonCopyService.sendCarbonCopy(input);
        verifyNoInteractions(notificationRpcService, eventPublisher);
    }

    @Test
    void sendCarbonCopy_ccUserIdsEmpty_shouldReturn() {
        CarbonCopyInput input = new CarbonCopyInput();
        input.setCcUserIds(Collections.emptyList());
        carbonCopyService.sendCarbonCopy(input);
        verifyNoInteractions(notificationRpcService, eventPublisher);
    }

    @Test
    void sendCarbonCopy_withValidInput_shouldSendAndPublishEvent() {
        CarbonCopyInput input = new CarbonCopyInput();
        input.setProcessInstanceId("proc1");
        input.setTaskId("task1");
        input.setCcUserIds(List.of("user1", "user2"));
        input.setTitle("标题");
        input.setContent("内容");
        input.setBusinessType("contract");
        input.setBusinessKey("bk1");

        carbonCopyService.sendCarbonCopy(input);

        verify(notificationRpcService).sendNotification(
                List.of("user1", "user2"), "标题", "内容", "contract", "bk1");
        verify(eventPublisher).publish(eq(WorkflowEventType.TASK_CC),
                eq("proc1"), isNull(), eq("bk1"),
                eq("task1"), isNull(), isNull(),
                eq("user1,user2"),
                isNull(), isNull(), isNull());
    }

    @Test
    void sendCarbonCopy_rpcThrowsException_shouldNotThrow() {
        CarbonCopyInput input = new CarbonCopyInput();
        input.setProcessInstanceId("proc1");
        input.setCcUserIds(List.of("user1"));
        input.setTitle("t");
        input.setContent("c");
        input.setBusinessType("bt");
        input.setBusinessKey("bk");

        doThrow(new RuntimeException("rpc error")).when(notificationRpcService)
                .sendNotification(anyList(), anyString(), anyString(), anyString(), anyString());

        // 不抛异常
        carbonCopyService.sendCarbonCopy(input);

        // 事件仍发布
        verify(eventPublisher).publish(eq(WorkflowEventType.TASK_CC),
                eq("proc1"), isNull(), eq("bk"),
                isNull(), isNull(), isNull(),
                eq("user1"),
                isNull(), isNull(), isNull());
    }

    // ==================== sendCarbonCopy(String, ...) ====================

    @Test
    void sendCarbonCopyString_blank_shouldReturn() {
        carbonCopyService.sendCarbonCopy("", "proc1", "key", "bk", "task1", "title", "content");
        verifyNoInteractions(notificationRpcService, eventPublisher);
    }

    @Test
    void sendCarbonCopyString_null_shouldReturn() {
        carbonCopyService.sendCarbonCopy((String) null, "proc1", "key", "bk", "task1", "title", "content");
        verifyNoInteractions(notificationRpcService, eventPublisher);
    }

    @Test
    void sendCarbonCopyString_allBlanksAfterSplit_shouldReturn() {
        carbonCopyService.sendCarbonCopy(" , , ", "proc1", "key", "bk", "task1", "title", "content");
        verifyNoInteractions(notificationRpcService, eventPublisher);
    }

    @Test
    void sendCarbonCopyString_validCommaSeparated_shouldSend() {
        carbonCopyService.sendCarbonCopy("1001,1002, 1003", "proc1", "contract", "bk1", "task1", "title", "content");

        verify(notificationRpcService).sendNotification(
                eq(List.of("1001", "1002", "1003")), eq("title"), eq("content"), eq("contract"), eq("bk1"));
        verify(eventPublisher).publish(eq(WorkflowEventType.TASK_CC),
                eq("proc1"), isNull(), eq("bk1"),
                eq("task1"), isNull(), isNull(),
                anyString(), isNull(), isNull(), isNull());
    }
}

