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

    List<ProductOffer> findAllByProductIdAndStatusOrderByPriceAscIdAsc(Long productId, OfferStatus status);

    // 캐시와 가격이 같은 판매 중 오퍼만 연결한다. 동률이면 가장 작은 ID를 선택한다.
    @Query("""
        SELECT o FROM ProductOffer o
        JOIN ProductMinPrice m ON m.productId = o.productId AND m.minPrice = o.price
        WHERE o.productId IN :productIds AND o.status = 'ON_SALE'
          AND NOT EXISTS (
            SELECT other.id FROM ProductOffer other
            WHERE other.productId = o.productId AND other.price = o.price
              AND other.status = 'ON_SALE' AND other.id < o.id
          )
        """)
    List<ProductOffer> findLowestOnSaleOffers(List<Long> productIds);

    @Query("""
        SELECT o.id, o.productId, p.code, o.externalItemId, o.purchaseUrl, o.price, o.status
        FROM ProductOffer o
        JOIN Product p ON p.id = o.productId
        WHERE o.name = '쿠팡'
          AND o.status IN :statuses
          AND o.externalItemId IS NOT NULL AND TRIM(o.externalItemId) <> ''
        ORDER BY o.id
        """)
    List<PriceRefreshTarget> findCoupangPriceRefreshTargets(List<OfferStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProductOffer> findAllByProductIdOrderByIdAsc(Long productId);
}
