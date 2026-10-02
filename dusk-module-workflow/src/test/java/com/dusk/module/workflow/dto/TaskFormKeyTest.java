package com.dusk.module.workflow.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TaskFormKey} 单元测试：重点覆盖 candidateRoles/candidatePsns 的
 * 兼容性解析分支（null / String / List / 其他类型）。
 */
class TaskFormKeyTest {

    @Test
    @DisplayName("默认值：activiti 与 notice 均非空，通知开关默认开启")
    void defaults() {
        TaskFormKey formKey = new TaskFormKey();

        assertThat(formKey.getActiviti()).isNotNull();
        assertThat(formKey.getActiviti().getNotice()).isNotNull();
        assertThat(formKey.getActiviti().getNotice().isAddTodo()).isTrue();
        assertThat(formKey.getActiviti().getNotice().isAppPush()).isTrue();
        assertThat(formKey.getActiviti().isCallBackPre()).isFalse();
    }

    @Test
    @DisplayName("toStr：null 返回空字符串")
    void toStr_null() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();

        assertThat(activiti.getCandidateRoles()).isEmpty();
        assertThat(activiti.getCandidatePsns()).isEmpty();
    }

    @Test
    @DisplayName("toStr：字符串原样返回")
    void toStr_string() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        activiti.setCandidateRoles("roleA");
        activiti.setCandidatePsns("1001");

        assertThat(activiti.getCandidateRoles()).isEqualTo("roleA");
        assertThat(activiti.getCandidatePsns()).isEqualTo("1001");
    }

    @Test
    @DisplayName("toStr：列表以逗号拼接")
    void toStr_list() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        activiti.setCandidateRoles(List.of("roleA", "roleB"));
        activiti.setCandidatePsns(Arrays.asList(1001L, 1002L));

        assertThat(activiti.getCandidateRoles()).isEqualTo("roleA,roleB");
        assertThat(activiti.getCandidatePsns()).isEqualTo("1001,1002");
    }

    @Test
    @DisplayName("toStr：其他类型返回空字符串")
    void toStr_otherType() {
        TaskFormKey.Activiti activiti = new TaskFormKey.Activiti();
        activiti.setCandidateRoles(123);
        activiti.setCandidatePsns(true);

        assertThat(activiti.getCandidateRoles()).isEmpty();
        assertThat(activiti.getCandidatePsns()).isEmpty();
    }
}
