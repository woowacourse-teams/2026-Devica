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
        return productOfferRepository.findCoupangPriceRefreshTargets(List.of(OfferStatus.ON_SALE, OfferStatus.HIDDEN));
    }

    @Transactional
    public PriceRefreshResult refreshCoupangPrice(PriceRefreshTarget target, long price, LocalDate checkedAt) {
        if (price <= 0 || checkedAt == null) {
            throw new IllegalArgumentException("확인한 가격은 양수이고 확인 날짜가 있어야 합니다.");
        }
        ProductOffer offer = findUnchangedTarget(target, checkedAt);
        if (offer == null) {
            return PriceRefreshResult.SKIPPED;
        }
        boolean restored = offer.getStatus() == OfferStatus.HIDDEN;
        boolean priceChanged = offer.getPrice() != price;
        offer.refreshPrice(price, checkedAt);
        if (priceChanged || restored) {
            refreshMinPrice(offer);
        }
        if (restored) {
            return PriceRefreshResult.RESTORED;
        }
        return priceChanged ? PriceRefreshResult.UPDATED : PriceRefreshResult.UNCHANGED;
    }

    @Transactional
    public PriceRefreshResult hideCoupangOffer(PriceRefreshTarget target, LocalDate observedAt) {
        if (observedAt == null) {
            throw new IllegalArgumentException("조회 날짜가 있어야 합니다.");
        }
        ProductOffer offer = findUnchangedTarget(target, observedAt);
        if (offer == null) {
            return PriceRefreshResult.SKIPPED;
        }
        if (offer.getStatus() == OfferStatus.HIDDEN) {
            return PriceRefreshResult.ALREADY_HIDDEN;
        }
        offer.hide();
        refreshMinPrice(offer);
        return PriceRefreshResult.HIDDEN;
    }

    private ProductOffer findUnchangedTarget(PriceRefreshTarget target, LocalDate observedAt) {
        // 같은 제품의 오퍼 갱신과 최저가 계산을 직렬화한다. API 호출은 트랜잭션 밖에서 한다.
        ProductOffer offer = productOfferRepository.findAllByProductIdOrderByIdAsc(target.productId()).stream()
            .filter(candidate -> candidate.getId().equals(target.offerId()))
            .findFirst()
            .orElse(null);
        if (offer == null || !"쿠팡".equals(offer.getName())
            || (offer.getStatus() != OfferStatus.ON_SALE && offer.getStatus() != OfferStatus.HIDDEN)
            || offer.getStatus() != target.status() || offer.getPrice() != target.price()
            || target.vendorItemId() == null || target.vendorItemId().isBlank()
            || !target.vendorItemId().equals(offer.getExternalItemId())
            || !offer.getPurchaseUrl().equals(target.purchaseUrl())
            || (offer.getCheckedAt() != null && offer.getCheckedAt().isAfter(observedAt))) {
            return null;
        }
        return offer;
    }

    private void refreshMinPrice(ProductOffer offer) {
        productOfferRepository.flush();
        productMinPriceRepository.refresh(offer.getProductId());
    }

    public enum PriceRefreshResult {
        UPDATED,
        UNCHANGED,
        HIDDEN,
        ALREADY_HIDDEN,
        RESTORED,
        SKIPPED
    }
}
