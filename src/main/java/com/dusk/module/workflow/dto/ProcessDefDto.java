package com.dusk.module.workflow.dto;

import com.github.dozermapper.core.Mapping;
import lombok.Data;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;

/**
 * @author kefuming
 * @date 2020-07-22 11:27
 */
@Data
public class ProcessDefDto {
    private String category;
    private String key;
    private String name;
    @Mapping("version")
    private Integer revision;
    private LocalDateTime deploymentTime;
    @Mapping("resourceName")
    private String xmlName;
    @Mapping("diagramResourceName")
    private String picName;
    private String deploymentId;
    private Boolean suspend;
    private String description;
}
