package com.wrb.devica.product;

import static com.wrb.devica.product.QCpu.cpu;
import static com.wrb.devica.product.QLaptop.laptop;
import static com.wrb.devica.product.QProductOffer.productOffer;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.util.StringUtils;

public class LaptopRepositoryCustomImpl implements LaptopRepositoryCustom {

    private static final double CPU_BASE_SCORE = 20_000;
    private static final double MEMORY_BASE_GB = 16;
    private static final double STORAGE_BASE_GB = 512;
    private static final double CPU_WEIGHT = 0.4;
    private static final double MEMORY_WEIGHT = 0.4;
    private static final double STORAGE_WEIGHT = 0.2;
    private static final double MAX_SPEC_RATIO = 2.0;

    private final JPAQueryFactory queryFactory;

    public LaptopRepositoryCustomImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Slice<LaptopSummaryResponse> findSummariesWithMinPriceByCondition(LaptopSearchCondition condition, SortType sort, Pageable pageable) {
        int pageSize = pageable.getPageSize();

        List<LaptopSummaryResponse> found = queryFactory
            .select(new QLaptopSummaryResponse(
                laptop.id,
                laptop.brand,
                laptop.name,
                productOffer.price.min(),
                laptop.os,
                cpu.name,
                cpu.coreCount,
                laptop.memoryGb,
                laptop.storageGb,
                laptop.screenSizeInch
            ))
            .from(laptop)
            .join(laptop.cpu, cpu)
            .leftJoin(productOffer).on(
                productOffer.product.id.eq(laptop.id),
                productOffer.status.eq(OfferStatus.ON_SALE)
            )
            .where(
                osEq(condition.os()),
                cpuScoreGoe(condition.cpuScore()),
                memoryGbGoe(condition.memoryGb()),
                storageGbGoe(condition.storageGb()),
                keywordContains(condition.keyword()),
                brandEq(condition.brand())
            )
            .groupBy(laptop.id, cpu.id)
            .having(
                minPriceGoe(condition.minPrice()),
                minPriceLoe(condition.maxPrice())
            )
            .orderBy(orderBy(sort))
            .offset(pageable.getOffset())
            .limit(pageSize + 1L)
            .fetch();

        boolean hasNext = found.size() > pageSize;

        if (hasNext) {
            found = found.subList(0, pageSize);
        }

        return new SliceImpl<>(found, pageable, hasNext);
    }

    private OrderSpecifier<?>[] orderBy(SortType sort) {
        OrderSpecifier<?> sortOrder = sortOrder(sort);

        if (sortOrder == null) {
            return new OrderSpecifier<?>[]{laptop.id.asc()};
        }
        return new OrderSpecifier<?>[]{sortOrder, laptop.id.asc()};
    }

    private OrderSpecifier<?> sortOrder(SortType sort) {
        if (sort == null) {
            return null;
        }

        NumberExpression<Long> minPrice = productOffer.price.min();
        return switch (sort) {
            case RECOMMENDED -> recommendedOrder();
            case PRICE_ASC -> minPrice.asc().nullsLast();
            case PRICE_DESC -> minPrice.desc().nullsLast();
        };
    }

    // 추천순은 가성비가 높은 순이다. 추천 방식을 바꾸려면 이 메서드만 교체한다
    private OrderSpecifier<?> recommendedOrder() {
        return specScorePerMinPrice().desc().nullsLast();
    }

    // 가성비 = 사양 점수 ÷ 최저가. 최저가가 없으면 null 이 되어 맨 뒤로 간다
    private NumberExpression<Double> specScorePerMinPrice() {
        return specScore().divide(productOffer.price.min());
    }

    // 사양 점수 = 백엔드 개발 권장 사양 대비 배율(최대 2배)의 가중합
    private NumberExpression<Double> specScore() {
        return weightedRatio(cpu.score, CPU_BASE_SCORE, CPU_WEIGHT)
            .add(weightedRatio(laptop.memoryGb, MEMORY_BASE_GB, MEMORY_WEIGHT))
            .add(weightedRatio(laptop.storageGb, STORAGE_BASE_GB, STORAGE_WEIGHT));
    }

    // 상수를 바인딩 파라미터로 넘기면 모든 노트북의 점수가 같게 계산됐다. 리터럴로 넣는다
    private NumberExpression<Double> weightedRatio(NumberExpression<Integer> value, double base, double weight) {
        String template = "least({0} / " + base + ", " + MAX_SPEC_RATIO + ") * " + weight;
        return Expressions.numberTemplate(Double.class, template, value);
    }

    private BooleanExpression minPriceGoe(Long minPrice) {
        if (minPrice == null) {
            return null;
        }
        return productOffer.price.min().goe(minPrice);
    }

    private BooleanExpression minPriceLoe(Long maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return productOffer.price.min().loe(maxPrice);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return laptop.brand.containsIgnoreCase(keyword)
            .or(laptop.name.containsIgnoreCase(keyword));
    }

    private BooleanExpression brandEq(String brand) {
        if (!StringUtils.hasText(brand)) {
            return null;
        }
        return laptop.brand.eq(brand);
    }

    private BooleanExpression osEq(Os os) {
        if (os == null) {
            return null;
        }
        return laptop.os.eq(os);
    }

    private BooleanExpression cpuScoreGoe(Integer cpuScore) {
        if (cpuScore == null) {
            return null;
        }
        return cpu.score.goe(cpuScore);
    }

    private BooleanExpression memoryGbGoe(Integer memoryGb) {
        if (memoryGb == null) {
            return null;
        }
        return laptop.memoryGb.goe(memoryGb);
    }

    private BooleanExpression storageGbGoe(Integer storageGb) {
        if (storageGb == null) {
            return null;
        }
        return laptop.storageGb.goe(storageGb);
    }
}
