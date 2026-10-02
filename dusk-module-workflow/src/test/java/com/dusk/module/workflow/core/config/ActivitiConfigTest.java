package com.dusk.module.workflow.core.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ActivitiConfig} 单元测试：校验引擎定制参数被正确注入。
 */
@ExtendWith(MockitoExtension.class)
class ActivitiConfigTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("getProcessEngineConfiguration：注入字体、ID生成器、数据源与自动建表")
    void getProcessEngineConfiguration() {
        ActivitiConfig config = new ActivitiConfig();
        SnowFlakeGenerator generator = new SnowFlakeGenerator();
        config.snowFlakeGenerator = generator;

        SpringProcessEngineConfiguration engineConfiguration =
                config.getProcessEngineConfiguration(dataSource, transactionManager);

        assertThat(engineConfiguration.getActivityFontName()).isEqualTo("宋体");
        assertThat(engineConfiguration.getAnnotationFontName()).isEqualTo("宋体");
        assertThat(engineConfiguration.getLabelFontName()).isEqualTo("宋体");
        assertThat(engineConfiguration.getIdGenerator()).isSameAs(generator);
        // 引擎内部会包装数据源，这里只校验已被注入
        assertThat(engineConfiguration.getDataSource()).isNotNull();
        assertThat(engineConfiguration.getTransactionManager()).isSameAs(transactionManager);
        assertThat(engineConfiguration.getDatabaseSchemaUpdate()).isEqualTo("true");
    }
}
