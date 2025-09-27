package com.dusk.workflow.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author kefuming
 * @date 2020-07-22 14:15
 */
@Data
public class FormPropertyDto implements Serializable {
    private String id;
    private String name;
    private String value;
}
