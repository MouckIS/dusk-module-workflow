package com.dusk.module.ddm.module.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author kefuming
 * @date 2020-07-21 16:32
 */
@SpringBootApplication(scanBasePackages = {
        "com.dusk.module.workflow",
        "com.dusk.common.core", "com.dusk.common.module.activiti"
}, exclude = {
        org.activiti.spring.boot.SecurityAutoConfiguration.class})
public class DuskWorkflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(DuskWorkflowApplication.class, args);
    }
}
