package com.wrb.devica.product.service;

import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.ProductOffer;
import com.wrb.devica.product.dto.PriceRefreshTarget;
import com.wrb.devica.product.repository.ProductMinPriceRepository;
import com.wrb.devica.product.repository.ProductOfferRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class ProductOfferService {

    private final ProductOfferRepository productOfferRepository;
    private final ProductMinPriceRepository productMinPriceRepository;

    public List<ProductOffer> findOnSaleOffers(Long productId) {
        return productOfferRepository.findAllByProductIdAndStatusOrderByPriceAsc(productId, OfferStatus.ON_SALE);
    }

    public List<PriceRefreshTarget> findCoupangPriceRefreshTargets() {
        return productOfferRepository.findCoupangPriceRefreshTargets(OfferStatus.ON_SALE);
    }

    @Transactional
    public PriceRefreshResult refreshCoupangPrice(PriceRefreshTarget target, long price, LocalDate checkedAt) {
        if (price <= 0 || checkedAt == null) {
            throw new IllegalArgumentException("확인한 가격은 양수이고 확인 날짜가 있어야 합니다.");
        }
        // 같은 제품의 오퍼 갱신을 직렬화하여 최저가 계산도 함께 보호한다. API 호출은 이 트랜잭션 밖에서 한다.
        ProductOffer offer = productOfferRepository.findAllByProductIdOrderByIdAsc(target.productId()).stream()
            .filter(candidate -> candidate.getId().equals(target.offerId()))
            .findFirst()
            .orElse(null);
        if (offer == null || !"쿠팡".equals(offer.getName()) || offer.getStatus() != OfferStatus.ON_SALE
            || target.vendorItemId() == null || target.vendorItemId().isBlank()
            || !target.vendorItemId().equals(offer.getExternalItemId())
            || (offer.getCheckedAt() != null && offer.getCheckedAt().isAfter(checkedAt))) {
            return PriceRefreshResult.SKIPPED;
        }
        boolean priceChanged = offer.getPrice() != price;
        offer.refreshPrice(price, checkedAt);
        if (priceChanged) {
            productOfferRepository.flush();
            productMinPriceRepository.refresh(offer.getProductId());
            return PriceRefreshResult.UPDATED;
        }
        return PriceRefreshResult.UNCHANGED;
    }

    public enum PriceRefreshResult {
        UPDATED,
        UNCHANGED,
        SKIPPED
    }
}
