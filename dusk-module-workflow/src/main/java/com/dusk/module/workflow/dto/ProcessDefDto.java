package com.dusk.module.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * @author kefuming
 * @date 2020-07-22 11:27
 */
@Getter
@Setter
public class ProcessDefDto {
    private String category;
    private String key;
    private String name;
    private Integer revision;
    private LocalDateTime deploymentTime;
    private String xmlName;
    private String picName;
    private String deploymentId;
    private Boolean suspend;
    private String description;
}
