package com.wrb.devica.fixture;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.test.context.TestComponent;

@TestComponent
public class FixtureSaver {

    @PersistenceContext
    private EntityManager entityManager;

    public <T> T save(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    public void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
