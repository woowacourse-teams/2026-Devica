package com.wrb.devica.common;

import com.wrb.devica.product.repository.ProductMinPriceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

// 테스트 데이터를 저장한 뒤 목록 최저가를 채운다. 계산 기준이 운영과 갈라지지 않도록 운영의 재계산을 그대로 쓴다.
@TestComponent
public class MinPriceRefresher {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private ProductMinPriceRepository productMinPriceRepository;

    @Transactional
    public void refresh(Long productId) {
        em.flush();
        productMinPriceRepository.refresh(productId);
    }
}
