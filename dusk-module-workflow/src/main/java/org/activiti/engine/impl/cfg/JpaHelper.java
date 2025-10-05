package org.activiti.engine.impl.cfg;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/**
 * @author : kefuming
 * @date : 2025/10/5 23:35
 */
public class JpaHelper {

    public static EntityManagerFactory createEntityManagerFactory(String jpaPersistenceUnitName) {
        return Persistence.createEntityManagerFactory(jpaPersistenceUnitName);
    }

}