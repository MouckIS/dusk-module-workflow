package com.dusk.module.workflow.core.config;

import com.dusk.common.core.jpa.Sequence;
import jakarta.annotation.Resource;
import org.flowable.common.engine.impl.cfg.IdGenerator;
import org.springframework.stereotype.Component;

/**
 * @author kefuming
 * @date 2022-06-22 15:19
 */
@Component
public class SnowFlakeGenerator implements IdGenerator {
    @Resource
    private Sequence sequence;

    @Override
    public String getNextId() {
        return String.valueOf(sequence.nextId());
    }
}
