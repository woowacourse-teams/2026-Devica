package com.wrb.devica.product.service;

import static com.wrb.devica.fixture.CpuFixture.cpu;
import static com.wrb.devica.fixture.LaptopFixture.laptop;
import static com.wrb.devica.fixture.ProductOfferFixture.offer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.wrb.devica.category.domain.ProductCategory;
import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.category.repository.ProductCategoryRepository;
import com.wrb.devica.common.JpaSliceTest;
import com.wrb.devica.product.domain.Laptop;
import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.ProductOffer;
import com.wrb.devica.product.dto.PriceRefreshTarget;
import com.wrb.devica.product.repository.CpuRepository;
import com.wrb.devica.product.repository.LaptopRepository;
import com.wrb.devica.product.repository.ProductMinPriceRepository;
import com.wrb.devica.product.repository.ProductOfferRepository;
import com.wrb.devica.product.service.ProductOfferService.PriceRefreshResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@JpaSliceTest
@Import(ProductOfferService.class)
class ProductOfferServiceTest {

    private static final LocalDate CHECKED_ON = LocalDate.of(2026, 10, 26);

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private CpuRepository cpuRepository;

    @Autowired
    private LaptopRepository laptopRepository;

    @Autowired
    private ProductOfferRepository productOfferRepository;

    @Autowired
    private ProductOfferService productOfferService;

    @MockitoSpyBean
    private ProductMinPriceRepository productMinPriceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 판매_중인_판매처만_가격_오름차순으로_반환한다() {
        // given
        Laptop laptop = laptopRepository.save(laptop()
            .category(productCategoryRepository.save(ProductCategory.from(ProductCategoryCode.LAPTOP)))
            .cpu(cpuRepository.save(cpu().build()))
            .build());

        productOfferRepository.save(offer().product(laptop).price(2_990_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(2_850_000L).status(OfferStatus.ON_SALE).build());
        productOfferRepository.save(offer().product(laptop).price(2_500_000L).status(OfferStatus.SOLD_OUT).build());

        // when & then
        assertThat(productOfferService.findOnSaleOffers(laptop.getId()))
            .extracting(ProductOffer::getPrice)
            .containsExactly(2_850_000L, 2_990_000L);
    }

    @Test
    void 판매_중이거나_숨겨진_기준_ID가_있는_쿠팡_오퍼를_조회한다() {
        Laptop laptop = saveLaptop();
        ProductOffer target = productOfferRepository.save(coupangOffer(laptop).build());
        ProductOffer hidden = productOfferRepository.save(coupangOffer(laptop).status(OfferStatus.HIDDEN).build());
        productOfferRepository.save(coupangOffer(laptop).name("다른 판매처").build());
        productOfferRepository.save(coupangOffer(laptop).status(OfferStatus.SOLD_OUT).build());
        productOfferRepository.save(coupangOffer(laptop).externalItemId(null).build());
        productOfferRepository.save(coupangOffer(laptop).externalItemId("   ").build());

        assertThat(productOfferService.findCoupangPriceRefreshTargets()).containsExactly(target(laptop, target), target(laptop, hidden));
    }

    @ParameterizedTest
    @ValueSource(longs = {800_000L, 1_300_000L})
    void 가격이_오르거나_내리면_오퍼와_목록_최저가를_함께_갱신한다(long newPrice) {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).build());
        initializeMinPrice(laptop);

        PriceRefreshResult result = productOfferService.refreshCoupangPrice(target(laptop, offer), newPrice, CHECKED_ON);
        flushAndClear();

        assertThat(result).isEqualTo(PriceRefreshResult.UPDATED);
        ProductOffer refreshed = productOfferRepository.findById(offer.getId()).orElseThrow();
        assertThat(refreshed.getPrice()).isEqualTo(newPrice);
        assertThat(refreshed.getCheckedAt()).isEqualTo(CHECKED_ON);
        assertThat(productMinPriceRepository.findById(laptop.getId()).orElseThrow().getMinPrice()).isEqualTo(newPrice);
    }

    @Test
    void 다른_판매처가_더_저렴하면_그_가격을_최저가로_유지하고_품절_가격은_제외한다() {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).build());
        productOfferRepository.save(coupangOffer(laptop).name("다른 판매처").price(900_000L).build());
        productOfferRepository.save(coupangOffer(laptop).status(OfferStatus.SOLD_OUT).price(500_000L).build());
        initializeMinPrice(laptop);

        productOfferService.refreshCoupangPrice(target(laptop, offer), 1_200_000L, CHECKED_ON);
        flushAndClear();

        assertThat(productMinPriceRepository.findById(laptop.getId()).orElseThrow().getMinPrice()).isEqualTo(900_000L);
    }

    @Test
    void 가격이_같아도_확인_날짜는_갱신한다() {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).build());
        initializeMinPrice(laptop);

        PriceRefreshResult result = productOfferService.refreshCoupangPrice(target(laptop, offer), 1_000_000L, CHECKED_ON);
        flushAndClear();

        assertThat(result).isEqualTo(PriceRefreshResult.UNCHANGED);
        ProductOffer refreshed = productOfferRepository.findById(offer.getId()).orElseThrow();
        assertThat(refreshed.getPrice()).isEqualTo(1_000_000L);
        assertThat(refreshed.getCheckedAt()).isEqualTo(CHECKED_ON);
        assertThat(productMinPriceRepository.findById(laptop.getId()).orElseThrow().getMinPrice()).isEqualTo(1_000_000L);
    }

    @Test
    void 같은_날_다시_확인한_가격도_갱신할_수_있다() {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).checkedAt(CHECKED_ON).build());

        PriceRefreshResult result = productOfferService.refreshCoupangPrice(target(laptop, offer), 900_000L, CHECKED_ON);

        assertThat(result).isEqualTo(PriceRefreshResult.UPDATED);
        assertThat(offer.getPrice()).isEqualTo(900_000L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "99999"})
    void 기준_ID가_없거나_조회_이후_연결이_바뀌면_갱신하지_않는다(String vendorItemId) {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).build());
        PriceRefreshTarget staleTarget = new PriceRefreshTarget(offer.getId(), laptop.getId(), laptop.getCode(), vendorItemId,
            offer.getPurchaseUrl(), offer.getPrice(), offer.getStatus());

        PriceRefreshResult result = productOfferService.refreshCoupangPrice(staleTarget, 900_000L, CHECKED_ON);

        assertThat(result).isEqualTo(PriceRefreshResult.SKIPPED);
        assertUnchanged(offer);
    }

    @ParameterizedTest
    @CsvSource({"다른 판매처, ON_SALE", "쿠팡, SOLD_OUT", "쿠팡, DISCONTINUED"})
    void 판매처나_판매_상태가_바뀌면_갱신하지_않는다(String seller, OfferStatus status) {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).name(seller).status(status).build());

        PriceRefreshResult result = productOfferService.refreshCoupangPrice(target(laptop, offer), 900_000L, CHECKED_ON);

        assertThat(result).isEqualTo(PriceRefreshResult.SKIPPED);
        assertUnchanged(offer);
    }

    @Test
    void 오퍼가_삭제되면_갱신하지_않는다() {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).build());
        PriceRefreshTarget target = target(laptop, offer);
        productOfferRepository.delete(offer);
        productOfferRepository.flush();

        assertThat(productOfferService.refreshCoupangPrice(target, 900_000L, CHECKED_ON))
            .isEqualTo(PriceRefreshResult.SKIPPED);
        assertThat(productMinPriceRepository.findById(laptop.getId())).isEmpty();
    }

    @Test
    void 이미_더_최근에_확인한_가격은_이전_날짜의_결과로_덮어쓰지_않는다() {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).checkedAt(CHECKED_ON.plusDays(1)).build());

        assertThat(productOfferService.refreshCoupangPrice(target(laptop, offer), 900_000L, CHECKED_ON))
            .isEqualTo(PriceRefreshResult.SKIPPED);
        assertThat(offer.getPrice()).isEqualTo(1_000_000L);
        assertThat(offer.getCheckedAt()).isEqualTo(CHECKED_ON.plusDays(1));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void 유효하지_않은_가격은_저장하지_않는다(long price) {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).build());

        assertThatThrownBy(() -> productOfferService.refreshCoupangPrice(target(laptop, offer), price, CHECKED_ON))
            .isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(offer);
    }

    @Test
    void 확인_날짜가_없으면_저장하지_않는다() {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).build());

        assertThatThrownBy(() -> productOfferService.refreshCoupangPrice(target(laptop, offer), 900_000L, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertUnchanged(offer);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 최저가_저장이_실패하면_가격과_숨김_변경도_함께_롤백한다(boolean hide) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Laptop laptop = transaction.execute(status -> saveLaptop());
        ProductOffer offer = transaction.execute(status -> {
            ProductOffer saved = productOfferRepository.save(coupangOffer(laptop).build());
            initializeMinPrice(laptop);
            return saved;
        });
        try {
            doAnswer(invocation -> {
                productMinPriceRepository.deleteForProduct(laptop.getId());
                throw new DataIntegrityViolationException("최저가 저장 실패");
            }).when(productMinPriceRepository).refresh(laptop.getId());

            assertThatThrownBy(() -> {
                if (hide) {
                    productOfferService.hideCoupangOffer(target(laptop, offer), CHECKED_ON);
                } else {
                    productOfferService.refreshCoupangPrice(target(laptop, offer), 900_000L, CHECKED_ON);
                }
            }).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(productOfferRepository.findById(offer.getId()).orElseThrow().getStatus())
                .isEqualTo(OfferStatus.ON_SALE);

            assertUnchanged(productOfferRepository.findById(offer.getId()).orElseThrow());
            assertThat(productMinPriceRepository.findById(laptop.getId()).orElseThrow().getMinPrice()).isEqualTo(1_000_000L);
        } finally {
            transaction.executeWithoutResult(status -> {
                productMinPriceRepository.deleteForProduct(laptop.getId());
                productOfferRepository.deleteById(offer.getId());
                laptopRepository.deleteById(laptop.getId());
                cpuRepository.deleteById(laptop.getCpu().getId());
                productCategoryRepository.deleteById(laptop.getCategory().getId());
            });
        }
    }

    @Test
    void 숨김은_가격과_확인일을_보존하고_판매_최저가를_제거한다() {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).build());
        initializeMinPrice(laptop);

        assertThat(productOfferService.hideCoupangOffer(target(laptop, offer), CHECKED_ON))
            .isEqualTo(PriceRefreshResult.HIDDEN);
        flushAndClear();

        ProductOffer hidden = productOfferRepository.findById(offer.getId()).orElseThrow();
        assertThat(hidden.getStatus()).isEqualTo(OfferStatus.HIDDEN);
        assertUnchanged(hidden);
        assertThat(productMinPriceRepository.findById(laptop.getId())).isEmpty();
        assertThat(productOfferService.findOnSaleOffers(laptop.getId())).isEmpty();
        assertThat(productOfferService.findCoupangPriceRefreshTargets()).containsExactly(target(laptop, hidden));
        assertThat(productOfferService.hideCoupangOffer(target(laptop, hidden), CHECKED_ON))
            .isEqualTo(PriceRefreshResult.ALREADY_HIDDEN);
    }

    @Test
    void 쿠팡을_숨겨도_다른_판매처의_최저가는_유지한다() {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).build());
        productOfferRepository.save(coupangOffer(laptop).name("다른 판매처").price(1_200_000L).build());
        initializeMinPrice(laptop);

        productOfferService.hideCoupangOffer(target(laptop, offer), CHECKED_ON);
        flushAndClear();

        assertThat(productMinPriceRepository.findById(laptop.getId()).orElseThrow().getMinPrice()).isEqualTo(1_200_000L);
    }

    @ParameterizedTest
    @ValueSource(longs = {1_000_000L, 900_000L})
    void 숨긴_상품의_가격을_다시_확인하면_노출과_최저가를_복구한다(long price) {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).status(OfferStatus.HIDDEN).build());

        assertThat(productOfferService.refreshCoupangPrice(target(laptop, offer), price, CHECKED_ON))
            .isEqualTo(PriceRefreshResult.RESTORED);
        flushAndClear();

        ProductOffer restored = productOfferRepository.findById(offer.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(OfferStatus.ON_SALE);
        assertThat(restored.getCheckedAt()).isEqualTo(CHECKED_ON);
        assertThat(productMinPriceRepository.findById(laptop.getId()).orElseThrow().getMinPrice()).isEqualTo(price);
    }

    @Test
    void 조회_중_가격이_바뀐_상품은_이전_결과로_숨기거나_갱신하지_않는다() {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).build());
        PriceRefreshTarget snapshot = target(laptop, offer);
        offer.refreshPrice(900_000L, CHECKED_ON);

        assertThat(productOfferService.hideCoupangOffer(snapshot, CHECKED_ON)).isEqualTo(PriceRefreshResult.SKIPPED);
        assertThat(productOfferService.refreshCoupangPrice(snapshot, 800_000L, CHECKED_ON))
            .isEqualTo(PriceRefreshResult.SKIPPED);
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.ON_SALE);
        assertThat(offer.getPrice()).isEqualTo(900_000L);
    }

    @Test
    void 조회_중_숨김_상태가_바뀌면_이전_결과로_복구하지_않는다() {
        Laptop laptop = saveLaptop();
        ProductOffer offer = productOfferRepository.save(coupangOffer(laptop).build());
        PriceRefreshTarget snapshot = target(laptop, offer);
        offer.hide();

        assertThat(productOfferService.refreshCoupangPrice(snapshot, 800_000L, CHECKED_ON))
            .isEqualTo(PriceRefreshResult.SKIPPED);
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.HIDDEN);
    }

    private Laptop saveLaptop() {
        return laptopRepository.save(laptop()
            .category(productCategoryRepository.save(ProductCategory.from(ProductCategoryCode.LAPTOP)))
            .cpu(cpuRepository.save(cpu().build()))
            .build());
    }

    private ProductOffer.ProductOfferBuilder coupangOffer(Laptop laptop) {
        return ProductOffer.builder()
            .productId(laptop.getId())
            .name("쿠팡")
            .price(1_000_000L)
            .externalItemId("12345")
            .purchaseUrl("https://www.coupang.com/vp/products/111?itemId=222&vendorItemId=12345")
            .status(OfferStatus.ON_SALE)
            .checkedAt(CHECKED_ON.minusDays(1));
    }

    private PriceRefreshTarget target(Laptop laptop, ProductOffer offer) {
        return new PriceRefreshTarget(offer.getId(), laptop.getId(), laptop.getCode(), offer.getExternalItemId(),
            offer.getPurchaseUrl(), offer.getPrice(), offer.getStatus());
    }

    private void initializeMinPrice(Laptop laptop) {
        productOfferRepository.flush();
        productMinPriceRepository.refresh(laptop.getId());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void assertUnchanged(ProductOffer offer) {
        assertThat(offer.getPrice()).isEqualTo(1_000_000L);
        assertThat(offer.getCheckedAt()).isEqualTo(CHECKED_ON.minusDays(1));
    }
}
