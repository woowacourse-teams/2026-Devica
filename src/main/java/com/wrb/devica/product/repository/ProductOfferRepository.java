package com.wrb.devica.product.repository;

import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.ProductOffer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOfferRepository extends JpaRepository<ProductOffer, Long> {

    List<ProductOffer> findAllByProductIdAndStatusOrderByPriceAsc(Long productId, OfferStatus status);
}
