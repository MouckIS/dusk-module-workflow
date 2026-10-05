package com.dusk.module.workflow.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModelFormDto} 读写测试。
 */
class ModelFormDtoTest {

    @Test
    @DisplayName("ModelFormDto：读写流程模型表单字段")
    void gettersAndSetters() {
        ModelFormDto dto = new ModelFormDto();
        dto.setCategory("cat");
        dto.setName("请假流程");
        dto.setKey("leave");
        dto.setDesc("描述");

        assertThat(dto.getCategory()).isEqualTo("cat");
        assertThat(dto.getName()).isEqualTo("请假流程");
        assertThat(dto.getKey()).isEqualTo("leave");
        assertThat(dto.getDesc()).isEqualTo("描述");
    }
}
