package com.dusk.module.workflow;

import lombok.SneakyThrows;
import com.dusk.module.workflow.service.IWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author kefuming
 * @date 2020-12-09 17:20
 */
@SpringBootTest
 class DuskWorkflowApplicationTests {

    @Autowired
    IWorkflowService workflowService;

    @Test
    @SneakyThrows
    void contextLoads() {
//        TenantContextHolder.setTenantId(1278479637352165377L);
//        WorkflowProcessDto dto=new WorkflowProcessDto();
//        dto.setBusinessKey("123");
//        dto.setProcessDefinitionKey("ticketCurrent");
//        dto.setTitle("测试标题");
//        dto.setType("type");
//        dto.setTypeName("显示标题");
//        Map<String,Object> var=new HashMap<>();
//        var.put("assigner","123");
//        dto.setVariables(var);
//        workflowService.startProcess(dto);
    }
}
