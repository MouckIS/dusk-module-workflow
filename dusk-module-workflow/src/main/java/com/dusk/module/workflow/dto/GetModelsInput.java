package com.dusk.module.workflow.dto;

import com.dusk.common.core.dto.PagedAndSortedInputDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kefuming
 * @date 2020-07-22 10:24
 */
@Getter
@Setter
public class GetModelsInput extends PagedAndSortedInputDto {

    @Schema(description = "名称")
    private String name;

}
