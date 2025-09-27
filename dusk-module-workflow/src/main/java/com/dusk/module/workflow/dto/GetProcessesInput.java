package com.dusk.module.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import com.dusk.common.core.dto.PagedAndSortedInputDto;

/**
 * @author kefuming
 * @date 2020-07-22 11:33
 */
@Data
public class GetProcessesInput extends PagedAndSortedInputDto {
    @ApiModelProperty("分类")
    private String category;
}
