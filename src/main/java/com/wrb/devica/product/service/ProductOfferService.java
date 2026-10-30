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
        validatePriceRefresh(price, checkedAt);
        ProductOffer offer = findUnchangedTarget(target, checkedAt);
        if (offer == null) {
            return PriceRefreshResult.SKIPPED;
        }
        return refreshOfferPrice(offer, price, checkedAt);
    }

    private void validatePriceRefresh(long price, LocalDate checkedAt) {
        if (price <= 0 || checkedAt == null) {
            throw new IllegalArgumentException("확인한 가격은 양수이고 확인 날짜가 있어야 합니다.");
        }
    }

    private PriceRefreshResult refreshOfferPrice(ProductOffer offer, long price, LocalDate checkedAt) {
        boolean restored = offer.getStatus() == OfferStatus.HIDDEN;
        boolean priceChanged = offer.getPrice() != price;
        offer.refreshPrice(price, checkedAt);
        if (priceChanged || restored) {
            refreshMinPrice(offer);
        }
        return determinePriceRefreshResult(restored, priceChanged);
    }

    private PriceRefreshResult determinePriceRefreshResult(boolean restored, boolean priceChanged) {
        if (restored) {
            return PriceRefreshResult.RESTORED;
        }
        if (priceChanged) {
            return PriceRefreshResult.UPDATED;
        }
        return PriceRefreshResult.UNCHANGED;
    }

    @Transactional
    public PriceRefreshResult hideCoupangOffer(PriceRefreshTarget target, LocalDate observedAt) {
        validateObservedAt(observedAt);
        ProductOffer offer = findUnchangedTarget(target, observedAt);
        if (offer == null) {
            return PriceRefreshResult.SKIPPED;
        }
        return hideOffer(offer);
    }

    private void validateObservedAt(LocalDate observedAt) {
        if (observedAt == null) {
            throw new IllegalArgumentException("조회 날짜가 있어야 합니다.");
        }
    }

    private PriceRefreshResult hideOffer(ProductOffer offer) {
        if (offer.getStatus() == OfferStatus.HIDDEN) {
            return PriceRefreshResult.ALREADY_HIDDEN;
        }
        offer.hide();
        refreshMinPrice(offer);
        return PriceRefreshResult.HIDDEN;
    }

    private ProductOffer findUnchangedTarget(PriceRefreshTarget target, LocalDate observedAt) {
        ProductOffer offer = findLockedOffer(target);
        if (!isUnchangedTarget(offer, target, observedAt)) {
            return null;
        }
        return offer;
    }

    private ProductOffer findLockedOffer(PriceRefreshTarget target) {
        // 같은 제품의 오퍼 갱신과 최저가 계산을 직렬화한다. API 호출은 트랜잭션 밖에서 한다.
        return productOfferRepository.findAllByProductIdOrderByIdAsc(target.productId()).stream()
            .filter(candidate -> candidate.getId().equals(target.offerId()))
            .findFirst()
            .orElse(null);
    }

    private boolean isUnchangedTarget(ProductOffer offer, PriceRefreshTarget target, LocalDate observedAt) {
        return offer != null && "쿠팡".equals(offer.getName())
            && hasRefreshableStatus(offer)
            && offer.getStatus() == target.status() && offer.getPrice() == target.price()
            && hasMatchingVendorItem(offer, target)
            && offer.getPurchaseUrl().equals(target.purchaseUrl())
            && !isCheckedAfter(offer, observedAt);
    }

    private boolean hasRefreshableStatus(ProductOffer offer) {
        return offer.getStatus() == OfferStatus.ON_SALE || offer.getStatus() == OfferStatus.HIDDEN;
    }

    private boolean hasMatchingVendorItem(ProductOffer offer, PriceRefreshTarget target) {
        return target.vendorItemId() != null && !target.vendorItemId().isBlank()
            && target.vendorItemId().equals(offer.getExternalItemId());
    }

    private boolean isCheckedAfter(ProductOffer offer, LocalDate observedAt) {
        return offer.getCheckedAt() != null && offer.getCheckedAt().isAfter(observedAt);
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
