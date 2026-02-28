package com.dusk.module.workflow.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskFormKey 单元测试
 * 覆盖Activiti.toStr()全部分支：null、String、List、其他类型；Notice和CarbonCopy默认值
 */
class TaskFormKeyTest {

    // ==================== toStr 分支覆盖 ====================

    @Test
    void toStr_null_shouldReturnEmptyString() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        activiti.setCandidateRoles(null);
        assertEquals("", activiti.getCandidateRoles());
    }

    @Test
    void toStr_string_shouldReturnSameString() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        activiti.setCandidateRoles("部门经理,总监");
        assertEquals("部门经理,总监", activiti.getCandidateRoles());
    }

    @Test
    void toStr_list_shouldJoinWithComma() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        activiti.setCandidateRoles(Arrays.asList("部门经理", "总监", "副总"));
        assertEquals("部门经理,总监,副总", activiti.getCandidateRoles());
    }

    @Test
    void toStr_emptyList_shouldReturnEmptyString() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        activiti.setCandidateRoles(Collections.emptyList());
        assertEquals("", activiti.getCandidateRoles());
    }

    @Test
    void toStr_otherType_shouldReturnEmptyString() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        activiti.setCandidateRoles(12345); // Integer
        assertEquals("", activiti.getCandidateRoles());
    }

    @Test
    void candidatePsns_string_shouldReturnSameString() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        activiti.setCandidatePsns("1001,1002");
        assertEquals("1001,1002", activiti.getCandidatePsns());
    }

    @Test
    void candidatePsns_null_shouldReturnEmptyString() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        activiti.setCandidatePsns(null);
        assertEquals("", activiti.getCandidatePsns());
    }

    @Test
    void candidatePsns_list_shouldJoinWithComma() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        activiti.setCandidatePsns(Arrays.asList(1001, 1002));
        assertEquals("1001,1002", activiti.getCandidatePsns());
    }

    // ==================== 默认值测试 ====================

    @Test
    void taskFormKey_defaults() {
        TaskFormKey formKey = new TaskFormKey();
        assertNotNull(formKey.getActiviti());
        assertFalse(formKey.getActiviti().isCallBackPre());
    }

    @Test
    void notice_defaults() {
        TaskFormKey.Notice notice = new TaskFormKey.Notice();
        assertTrue(notice.isAddTodo());
        assertTrue(notice.isAppPush());
    }

    @Test
    void carbonCopy_defaults() {
        TaskFormKey.CarbonCopy cc = new TaskFormKey.CarbonCopy();
        assertFalse(cc.isEnabled());
        assertNull(cc.getCcUserIds());
        assertNull(cc.getCcRoles());
        assertNull(cc.getTemplate());
    }

    @Test
    void activiti_notice_default() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        assertNotNull(activiti.getNotice());
        assertTrue(activiti.getNotice().isAddTodo());
    }

    @Test
    void activiti_carbonCopy_default() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        assertNotNull(activiti.getCarbonCopy());
        assertFalse(activiti.getCarbonCopy().isEnabled());
    }
}

