package com.wrb.devica.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrb.devica.category.ProductCategory;
import com.wrb.devica.category.ProductCategoryName;
import com.wrb.devica.common.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

// 운영과 같은 DBMS 로 검증한다(ADR 0008). 임베디드 DB 로 바꾸지 않는다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class LaptopRepositoryTest {

    @Autowired
    private LaptopRepository laptopRepository;

    @Autowired
    private EntityManager entityManager;

    private ProductCategory category;

    @BeforeEach
    void setUp() {
        category = ProductCategory.from(ProductCategoryName.LAPTOP);
        entityManager.persist(category);
    }

    @Test
    void 조건이_없을_때_조회하면_전체를_id_오름차순으로_반환한다() {
        // given
        saveLaptop("A", Os.WINDOWS, 10000, 16, 512);
        saveLaptop("B", Os.MAC, 20000, 32, 1024);

        // when
        Slice<Laptop> found = findLaptops(null, null, null, null, 0, 10);

        // then
        assertThat(found.getContent()).extracting(Laptop::getName).containsExactly("A", "B");
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void os를_지정할_때_조회하면_해당_os만_반환한다() {
        // given
        saveLaptop("윈도우", Os.WINDOWS, 10000, 16, 512);
        saveLaptop("맥", Os.MAC, 10000, 16, 512);

        // when
        Slice<Laptop> found = findLaptops(Os.MAC, null, null, null, 0, 10);

        // then
        assertThat(found.getContent()).extracting(Laptop::getName).containsExactly("맥");
    }

    @Test
    void cpu_점수를_지정할_때_조회하면_그_이상만_반환한다() {
        // given
        saveLaptop("낮음", Os.WINDOWS, 9999, 16, 512);
        saveLaptop("같음", Os.WINDOWS, 10000, 16, 512);
        saveLaptop("높음", Os.WINDOWS, 10001, 16, 512);

        // when
        Slice<Laptop> found = findLaptops(null, 10000, null, null, 0, 10);

        // then
        assertThat(found.getContent()).extracting(Laptop::getName).containsExactly("같음", "높음");
    }

    @Test
    void 메모리와_저장장치를_지정할_때_조회하면_둘_다_만족하는_것만_반환한다() {
        // given
        saveLaptop("메모리만", Os.WINDOWS, 10000, 32, 256);
        saveLaptop("저장장치만", Os.WINDOWS, 10000, 8, 1024);
        saveLaptop("둘다", Os.WINDOWS, 10000, 32, 1024);

        // when
        Slice<Laptop> found = findLaptops(null, null, 16, 512, 0, 10);

        // then
        assertThat(found.getContent()).extracting(Laptop::getName).containsExactly("둘다");
    }

    @Test
    void 조건을_여러_개_지정할_때_조회하면_모두_만족하는_것만_반환한다() {
        // given
        saveLaptop("맥_고사양", Os.MAC, 20000, 32, 1024);
        saveLaptop("맥_저사양", Os.MAC, 5000, 32, 1024);
        saveLaptop("윈도우_고사양", Os.WINDOWS, 20000, 32, 1024);

        // when
        Slice<Laptop> found = findLaptops(Os.MAC, 10000, 16, 512, 0, 10);

        // then
        assertThat(found.getContent()).extracting(Laptop::getName).containsExactly("맥_고사양");
    }

    @Test
    void 다음_페이지가_있을_때_조회하면_hasNext가_참이다() {
        // given
        saveLaptop("1", Os.WINDOWS, 10000, 16, 512);
        saveLaptop("2", Os.WINDOWS, 10000, 16, 512);
        saveLaptop("3", Os.WINDOWS, 10000, 16, 512);

        // when
        Slice<Laptop> firstPage = findLaptops(null, null, null, null, 0, 2);

        // then
        assertThat(firstPage.getContent()).extracting(Laptop::getName).containsExactly("1", "2");
        assertThat(firstPage.hasNext()).isTrue();
    }

    @Test
    void 마지막_페이지를_조회하면_hasNext가_거짓이다() {
        // given
        saveLaptop("1", Os.WINDOWS, 10000, 16, 512);
        saveLaptop("2", Os.WINDOWS, 10000, 16, 512);
        saveLaptop("3", Os.WINDOWS, 10000, 16, 512);

        // when
        Slice<Laptop> lastPage = findLaptops(null, null, null, null, 1, 2);

        // then
        assertThat(lastPage.getContent()).extracting(Laptop::getName).containsExactly("3");
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    void 조건에_맞는_것이_없을_때_조회하면_빈_결과를_반환한다() {
        // given
        saveLaptop("유일", Os.WINDOWS, 10000, 16, 512);

        // when
        Slice<Laptop> found = findLaptops(Os.MAC, null, null, null, 0, 10);

        // then
        assertThat(found.getContent()).isEmpty();
        assertThat(found.hasNext()).isFalse();
    }

    @Test
    void 판매_중인_오퍼가_없을_때_조회하면_결과에서_제외된다() {
        // given
        saveLaptop("판매중", Os.WINDOWS, 10000, 16, 512);
        persistLaptop("오퍼없음", Os.WINDOWS, 10000, 16, 512);

        // when
        Slice<Laptop> found = findLaptops(null, null, null, null, 0, 10);

        // then
        assertThat(found.getContent()).extracting(Laptop::getName).containsExactly("판매중");
    }

    @Test
    void 오퍼가_모두_판매_중이_아닐_때_조회하면_결과에서_제외된다() {
        // given
        Laptop soldOut = persistLaptop("품절", Os.WINDOWS, 10000, 16, 512);
        saveOffer(soldOut, OfferStatus.SOLD_OUT);
        saveOffer(soldOut, OfferStatus.DISCONTINUED);

        // when
        Slice<Laptop> found = findLaptops(null, null, null, null, 0, 10);

        // then
        assertThat(found.getContent()).isEmpty();
    }

    private Slice<Laptop> findLaptops(Os os, Integer cpuScore, Integer memoryGb, Integer storageGb,
                                      int page, int size) {
        entityManager.flush();
        entityManager.clear();
        return laptopRepository.findAllByCondition(
            new LaptopSearchCondition(os, cpuScore, memoryGb, storageGb),
            PageRequest.of(page, size, Sort.by("id")));
    }

    private Laptop saveLaptop(String name, Os os, int cpuScore, int memoryGb, int storageGb) {
        Laptop laptop = persistLaptop(name, os, cpuScore, memoryGb, storageGb);
        saveOffer(laptop, OfferStatus.ON_SALE);
        return laptop;
    }

    private Laptop persistLaptop(String name, Os os, int cpuScore, int memoryGb, int storageGb) {
        Cpu cpu = Cpu.builder()
            .manufacturer("Intel")
            .name("Core " + cpuScore)
            .coreCount(8)
            .score(cpuScore)
            .build();
        entityManager.persist(cpu);

        Laptop laptop = Laptop.builder()
            .category(category)
            .brand("브랜드")
            .name(name)
            .code("CODE-" + name)
            .cpu(cpu)
            .os(os)
            .memoryGb(memoryGb)
            .storageGb(storageGb)
            .weightG(1200)
            .screenSizeInch(new BigDecimal("16.0"))
            .build();
        entityManager.persist(laptop);
        return laptop;
    }

    private void saveOffer(Laptop laptop, OfferStatus status) {
        entityManager.persist(ProductOffer.builder()
            .product(laptop)
            .name("판매처")
            .price(1_000_000L)
            .purchaseUrl("https://example.com/" + laptop.getName())
            .status(status)
            .build());
    }
}
