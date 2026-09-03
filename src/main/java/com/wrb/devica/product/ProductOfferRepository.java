package com.wrb.devica.product;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOfferRepository extends JpaRepository<ProductOffer, Long> {

    List<ProductOffer> findAllByProductIdAndStatusOrderByPriceAsc(Long productId, OfferStatus status);
}
