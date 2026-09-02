package com.wrb.devica.product;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOfferRepository extends JpaRepository<ProductOffer, Long> {

    @Query("""
        select new com.wrb.devica.product.ProductMinPrice(offer.product.id, min(offer.price))
        from ProductOffer offer
        where offer.product.id in :productIds
          and offer.status = :status
        group by offer.product.id
        """)
    List<ProductMinPrice> findMinPrices(@Param("productIds") Collection<Long> productIds,
                                        @Param("status") OfferStatus status);
}
