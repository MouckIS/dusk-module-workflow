package com.dusk.module.workflow.core.config;

import jakarta.annotation.Resource;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * @author kefuming
 * @date 2020-07-22 14:37
 */
@Configuration
public class ActivitiConfig {
    @Resource
    SnowFlakeGenerator snowFlakeGenerator;

    @Bean
    public SpringProcessEngineConfiguration getProcessEngineConfiguration(DataSource dataSource, PlatformTransactionManager transactionManager) {
        SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();

        config.setActivityFontName("宋体");
        config.setAnnotationFontName("宋体");
        config.setLabelFontName("宋体");


        config.setIdGenerator(snowFlakeGenerator);
        //config.getDbSqlSessionFactory().setIdGenerator(snowFlakeGenerator);

        config.setDataSource(dataSource);
        config.setTransactionManager(transactionManager);
        config.setDatabaseSchemaUpdate("true");

        return config;
    }

}
