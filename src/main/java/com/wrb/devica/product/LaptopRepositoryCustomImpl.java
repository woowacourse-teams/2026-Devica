package com.wrb.devica.product;

import static com.wrb.devica.product.QCpu.cpu;
import static com.wrb.devica.product.QLaptop.laptop;
import static com.wrb.devica.product.QProductOffer.productOffer;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.util.StringUtils;

public class LaptopRepositoryCustomImpl implements LaptopRepositoryCustom {

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
            case PRICE_ASC -> minPrice.asc().nullsLast();
            case PRICE_DESC -> minPrice.desc().nullsLast();
        };
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
