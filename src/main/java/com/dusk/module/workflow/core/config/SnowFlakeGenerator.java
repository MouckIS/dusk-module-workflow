package com.dusk.module.workflow.core.config;

import org.activiti.engine.impl.cfg.IdGenerator;
import com.dusk.common.framework.jpa.Sequence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author kefuming
 * @date 2022-06-22 15:19
 */
@Component
public class SnowFlakeGenerator implements IdGenerator {
    @Autowired
    private Sequence sequence;

    @Override
    public String getNextId() {
        return String.valueOf(sequence.nextId());
    }
}
