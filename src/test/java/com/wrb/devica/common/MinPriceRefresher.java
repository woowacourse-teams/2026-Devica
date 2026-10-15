package com.wrb.devica.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

@TestComponent
public class MinPriceRefresher {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void refresh(Long productId) {
        em.flush();
        em.createNativeQuery("DELETE FROM product_min_price WHERE product_id = :productId")
            .setParameter("productId", productId)
            .executeUpdate();
        em.createNativeQuery("""
                INSERT INTO product_min_price (product_id, min_price)
                SELECT product_id, MIN(price)
                FROM product_offer
                WHERE product_id = :productId AND status = 'ON_SALE'
                GROUP BY product_id
                """)
            .setParameter("productId", productId)
            .executeUpdate();
    }
}
