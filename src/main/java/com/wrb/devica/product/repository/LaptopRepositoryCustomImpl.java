package com.wrb.devica.product.repository;

import static com.wrb.devica.product.domain.QCpu.cpu;
import static com.wrb.devica.product.domain.QLaptop.laptop;
import static com.wrb.devica.product.domain.QProductOffer.productOffer;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.OfferStatus;
import com.wrb.devica.product.domain.Os;
import com.wrb.devica.product.dto.LaptopSearchCondition;
import com.wrb.devica.product.dto.ProductSummaryResponse;
import com.wrb.devica.product.dto.QProductSummaryResponse;
import com.wrb.devica.product.dto.SortType;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
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
    public Slice<ProductSummaryResponse> findSummariesWithMinPrice(LaptopSearchCondition condition, SortType sort,
                                                                   Pageable pageable) {
        int pageSize = pageable.getPageSize();

        List<ProductSummaryResponse> found = queryFactory
            .select(new QProductSummaryResponse(
                laptop.id,
                laptop.brand,
                laptop.name,
                productOffer.price.min(),
                laptop.os,
                cpu.name,
                laptop.memoryGb,
                laptop.storageGb
            ))
            .from(laptop)
            .join(laptop.cpu, cpu)
            .leftJoin(productOffer).on(
                productOffer.productId.eq(laptop.id),
                productOffer.status.eq(OfferStatus.ON_SALE)
            )
            .where(
                osEq(condition.os()),
                cpuTierAtLeast(condition.cpuTier()),
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
        List<OrderSpecifier<?>> orders = new ArrayList<>(sortOrders(sort));
        orders.add(laptop.id.asc());
        return orders.toArray(OrderSpecifier[]::new);
    }

    private List<OrderSpecifier<?>> sortOrders(SortType sort) {
        if (sort == null) {
            return List.of();
        }

        NumberExpression<Long> minPrice = productOffer.price.min();
        return switch (sort) {
            case RECOMMENDED -> recommendedOrders();
            case PRICE_ASC -> List.of(minPrice.asc().nullsLast());
            case PRICE_DESC -> List.of(minPrice.desc().nullsLast());
        };
    }

    private List<OrderSpecifier<?>> recommendedOrders() {
        return List.of(cpu.score.desc(), laptop.memoryGb.desc(), laptop.storageGb.desc());
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

    // 등급은 우리가 정의한 분류라 실제 비교는 그 등급의 최소 점수로 한다
    private BooleanExpression cpuTierAtLeast(CpuTier cpuTier) {
        if (cpuTier == null) {
            return null;
        }
        return cpu.score.goe(cpuTier.getMinScore());
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
