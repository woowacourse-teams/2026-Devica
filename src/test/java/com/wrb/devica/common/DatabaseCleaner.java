package com.wrb.devica.common;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

@TestComponent
public class DatabaseCleaner {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private CacheManager cacheManager;

    private List<String> tableNames;

    @PostConstruct
    @SuppressWarnings("unchecked")
    void findTableNames() {
        tableNames = em.createNativeQuery("""
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
            """).getResultList();
    }

    @Transactional
    public void clear() {
        em.clear();

        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        for (String tableName : tableNames) {
            em.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate();
        }
        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();

        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }
}
