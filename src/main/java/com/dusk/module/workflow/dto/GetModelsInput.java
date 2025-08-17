package com.dusk.module.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import com.dusk.common.framework.dto.PagedAndSortedInputDto;

/**
 * @author kefuming
 * @date 2020-07-22 10:24
 */
@Data
public class GetModelsInput extends PagedAndSortedInputDto {

    @ApiModelProperty("名称")
    private String name;

}
