package com.dusk.module.ddm.module.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import com.dusk.common.core.dto.PagedAndSortedInputDto;

/**
 * @author kefuming
 * @date 2020-07-22 10:24
 */
@Data
public class GetModelsInput extends PagedAndSortedInputDto {

    @ApiModelProperty("名称")
    private String name;

}
