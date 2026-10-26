package com.wrb.devica.product.repository;

import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.ProductOffer;
import com.wrb.devica.product.dto.PriceRefreshTarget;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProductOfferRepository extends JpaRepository<ProductOffer, Long> {

    List<ProductOffer> findAllByProductIdAndStatusOrderByPriceAsc(Long productId, OfferStatus status);

    @Query("""
        SELECT o.id, o.productId, p.code, o.externalItemId
        FROM ProductOffer o
        JOIN Product p ON p.id = o.productId
        WHERE o.name = '쿠팡'
          AND o.status = :status
          AND o.externalItemId IS NOT NULL AND TRIM(o.externalItemId) <> ''
        ORDER BY o.id
        """)
    List<PriceRefreshTarget> findCoupangPriceRefreshTargets(OfferStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProductOffer> findAllByProductIdOrderByIdAsc(Long productId);
}
