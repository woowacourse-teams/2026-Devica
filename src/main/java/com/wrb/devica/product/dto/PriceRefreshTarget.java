package com.wrb.devica.product.dto;

import com.wrb.devica.product.domain.OfferStatus;

public record PriceRefreshTarget(Long offerId, Long productId, String productCode, String vendorItemId,
                                 String purchaseUrl, long price, OfferStatus status) {
}
