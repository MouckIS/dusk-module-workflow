package com.dusk.module.workflow.core.config;

import com.dusk.common.model.jpa.Sequence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link SnowFlakeGenerator} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class SnowFlakeGeneratorTest {

    @Mock
    private Sequence sequence;

    @InjectMocks
    private SnowFlakeGenerator snowFlakeGenerator;

    @Test
    @DisplayName("getNextId：返回序列号字符串")
    void getNextId() {
        when(sequence.nextId()).thenReturn(123456789L);

        assertThat(snowFlakeGenerator.getNextId()).isEqualTo("123456789");
    }
}
