package com.dusk.module.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author kefuming
 * @date 2020-07-22 10:07
 */
@Getter
@Setter
public class ModelDto implements Serializable {
    private String id;
    private String name;
    private String key;
    private String category;
    private LocalDateTime createTime;
    private LocalDateTime lastUpdateTime;
    private Integer version;
    private String tenantId;
    private String deploymentId;
    private String metaInfo;
}
