package com.dusk.module.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author kefuming
 * @date 2020-12-10 10:43
 */
@Getter
@Setter
public class TodoExtensionsDto {
    private String type;
    private String businessId;
    /**
     * 业务数据
     */
    private Map<String, Object> businessData;
}
