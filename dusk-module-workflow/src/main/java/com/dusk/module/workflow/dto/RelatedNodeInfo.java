package com.dusk.module.workflow.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * @author kefuming
 * @date 2022-12-06 10:30
 */
@Getter
@Setter
public class RelatedNodeInfo {
    private String assignee;
    private String formKey;
    private String taskDirection;
    private String name;
    private String nodeType;
}
