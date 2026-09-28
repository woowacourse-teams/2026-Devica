package com.wrb.devica.product.service;

import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.ProductOffer;
import com.wrb.devica.product.repository.ProductOfferRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class ProductOfferService {

    private final ProductOfferRepository productOfferRepository;

    public List<ProductOffer> findOnSaleOffers(Long productId) {
        return productOfferRepository.findAllByProductIdAndStatusOrderByPriceAsc(productId, OfferStatus.ON_SALE);
    }
}
