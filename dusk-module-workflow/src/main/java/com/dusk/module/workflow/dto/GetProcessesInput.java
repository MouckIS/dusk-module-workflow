package com.dusk.module.workflow.dto;

import com.dusk.common.core.dto.PagedAndSortedInputDto;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kefuming
 * @date 2020-07-22 11:33
 */
@Getter
@Setter
public class GetProcessesInput extends PagedAndSortedInputDto {
    @ApiModelProperty("分类")
    private String category;
}
