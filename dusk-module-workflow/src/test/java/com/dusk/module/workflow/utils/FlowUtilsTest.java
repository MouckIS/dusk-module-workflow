package com.dusk.module.workflow.utils;

import com.dusk.module.workflow.dto.TaskFormKey;
import com.dusk.workflow.IProcessDesHolder;
import com.dusk.workflow.dto.ProcessDesOutPutDto;
import com.dusk.workflow.dto.WorkflowTaskDto;
import com.dusk.workflow.service.IWorkFlowRpcService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FlowUtils} 分支覆盖测试。
 */
@ExtendWith(MockitoExtension.class)
class FlowUtilsTest {

    @Mock
    private IWorkFlowRpcService workFlowRpcService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FlowUtils flowUtils;

    /** 测试用的 {@link IProcessDesHolder} 实现。 */
    static class Holder implements IProcessDesHolder {
        private final String processInstanceId;
        private ProcessDesOutPutDto processDes;

        Holder(String processInstanceId) {
            this.processInstanceId = processInstanceId;
        }

        @Override
        public String getProcessInstanceId() {
            return processInstanceId;
        }

        @Override
        public ProcessDesOutPutDto getProcessDes() {
            return processDes;
        }

        @Override
        public void setProcessDes(ProcessDesOutPutDto processDes) {
            this.processDes = processDes;
        }
    }

    private static ProcessDesOutPutDto des(String processInstanceId) {
        ProcessDesOutPutDto dto = new ProcessDesOutPutDto();
        dto.setProcessInstanceId(processInstanceId);
        dto.setDescription("d-" + processInstanceId);
        return dto;
    }

    @Test
    @DisplayName("setProcessDes：全部为空白的流程id时，不调用RPC")
    void setProcessDes_allBlank_shouldReturnEarly() {
        List<Holder> holders = new ArrayList<>();
        holders.add(new Holder(null));
        holders.add(new Holder(""));
        holders.add(new Holder("   "));

        flowUtils.setProcessDes(holders);

        verify(workFlowRpcService, never()).getProcessDescription(anyList());
        assertThat(holders.get(0).getProcessDes()).isNull();
    }

    @Test
    @DisplayName("setProcessDes：空集合时，不调用RPC")
    void setProcessDes_emptyList_shouldReturnEarly() {
        flowUtils.setProcessDes(new ArrayList<Holder>());

        verify(workFlowRpcService, never()).getProcessDescription(anyList());
    }

    @Test
    @DisplayName("setProcessDes：命中与未命中都要覆盖（含break分支）")
    void setProcessDes_shouldMatchOnlyExistingIds() {
        Holder matched = new Holder("p1");
        Holder unmatched = new Holder("p2");
        Holder blank = new Holder(" ");

        when(workFlowRpcService.getProcessDescription(anyList()))
                .thenReturn(List.of(des("p1")));

        flowUtils.setProcessDes(List.of(matched, unmatched, blank));

        assertThat(matched.getProcessDes()).isNotNull();
        assertThat(matched.getProcessDes().getDescription()).isEqualTo("d-p1");
        assertThat(unmatched.getProcessDes()).isNull();
        assertThat(blank.getProcessDes()).isNull();
    }

    @Test
    @DisplayName("setProcessDes：单对象重载委托到集合重载")
    void setProcessDes_singleHolder() {
        Holder holder = new Holder("p1");
        when(workFlowRpcService.getProcessDescription(anyList())).thenReturn(List.of(des("p1")));

        flowUtils.setProcessDes((IProcessDesHolder) holder);

        assertThat(holder.getProcessDes()).isNotNull();
    }

    @Test
    @DisplayName("setProcessDes：存在无匹配项的流程id时内层循环完整走完（不触发break）")
    void setProcessDes_unmatchedDescriptionExhaustsInnerLoop() {
        Holder matched = new Holder("p1");
        Holder unmatched = new Holder("p2");

        when(workFlowRpcService.getProcessDescription(anyList()))
                .thenReturn(List.of(des("p1"), des("pX")));

        flowUtils.setProcessDes(List.of(matched, unmatched));

        assertThat(matched.getProcessDes().getDescription()).isEqualTo("d-p1");
        assertThat(unmatched.getProcessDes()).isNull();
    }

    @Test
    @DisplayName("getFormKey：空白与正常字符串")
    void getFormKey_blankAndNormal() {
        assertThat(flowUtils.getFormKey(null, TaskFormKey.class)).isNotNull();
        assertThat(flowUtils.getFormKey("  ", TaskFormKey.class)).isNotNull();
        assertThat(flowUtils.getFormKey("{\"activiti\":{\"callBackPre\":true}}", TaskFormKey.class)
                .getActiviti().isCallBackPre()).isTrue();
    }

    @Test
    @DisplayName("getNextTaskFormKey：variables为null时默认pass=true")
    void getNextTaskFormKey_nullVariables() {
        WorkflowTaskDto to = new WorkflowTaskDto();
        to.setTaskDirection("to");
        to.setFormKey("{}");
        when(workFlowRpcService.getRelateTask(eq("t1"), anyBoolean(), any())).thenReturn(List.of(to));

        assertThat(flowUtils.getNextTaskFormKey("t1", null, TaskFormKey.class)).isNotNull();
    }

    @Test
    @DisplayName("getNextTaskFormKey：variables中有pass=true和pass=false")
    void getNextTaskFormKey_passFlag() {
        WorkflowTaskDto to = new WorkflowTaskDto();
        to.setTaskDirection("TO");
        to.setFormKey("{}");
        WorkflowTaskDto from = new WorkflowTaskDto();
        from.setTaskDirection("FROM");
        from.setFormKey("{}");

        Map<String, Object> passTrue = new HashMap<>();
        passTrue.put("pass", true);
        when(workFlowRpcService.getRelateTask(eq("t1"), anyBoolean(), eq(passTrue))).thenReturn(List.of(to, from));
        assertThat(flowUtils.getNextTaskFormKey("t1", passTrue, TaskFormKey.class)).isNotNull();

        Map<String, Object> passFalse = new HashMap<>();
        passFalse.put("pass", false);
        when(workFlowRpcService.getRelateTask(eq("t1"), anyBoolean(), eq(passFalse))).thenReturn(List.of(to, from));
        assertThat(flowUtils.getNextTaskFormKey("t1", passFalse, TaskFormKey.class)).isNotNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("getNextTaskFormKey（已废弃）：pass=true 命中to方向")
    void deprecatedGetNextTaskFormKey_passTrue() {
        WorkflowTaskDto from = new WorkflowTaskDto();
        from.setTaskDirection("from");
        from.setFormKey("{}");
        WorkflowTaskDto to = new WorkflowTaskDto();
        to.setTaskDirection("to");
        to.setFormKey("{\"activiti\":{\"callBackPre\":true}}");

        when(workFlowRpcService.getRelateTask(eq("t1"), anyBoolean(), any())).thenReturn(List.of(from, to));

        TaskFormKey result = flowUtils.getNextTaskFormKey("t1", true, new HashMap<>(), TaskFormKey.class);
        assertThat(result.getActiviti().isCallBackPre()).isTrue();
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("getNextTaskFormKey（已废弃）：pass=false 命中from方向")
    void deprecatedGetNextTaskFormKey_passFalse() {
        WorkflowTaskDto from = new WorkflowTaskDto();
        from.setTaskDirection("from");
        from.setFormKey("{}");
        WorkflowTaskDto to = new WorkflowTaskDto();
        to.setTaskDirection("to");
        to.setFormKey("{}");

        when(workFlowRpcService.getRelateTask(eq("t1"), anyBoolean(), any())).thenReturn(List.of(to, from));

        assertThat(flowUtils.getNextTaskFormKey("t1", false, new HashMap<>(), TaskFormKey.class)).isNotNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("getNextTaskFormKey（已废弃）：无匹配方向返回null")
    void deprecatedGetNextTaskFormKey_noMatch() {
        WorkflowTaskDto from = new WorkflowTaskDto();
        from.setTaskDirection("from");
        from.setFormKey("{}");

        when(workFlowRpcService.getRelateTask(eq("t1"), anyBoolean(), any())).thenReturn(List.of(from));

        assertThat(flowUtils.getNextTaskFormKey("t1", true, new HashMap<>(), TaskFormKey.class)).isNull();
    }
}
