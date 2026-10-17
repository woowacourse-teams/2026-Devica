package com.wrb.devica.product.repository;

import static com.wrb.devica.product.domain.QCpu.cpu;
import static com.wrb.devica.product.domain.QLaptop.laptop;
import static com.wrb.devica.product.domain.QProductMinPrice.productMinPrice;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wrb.devica.product.domain.CpuTier;
import com.wrb.devica.product.domain.Os;
import com.wrb.devica.product.dto.LaptopSearchCondition;
import com.wrb.devica.product.dto.ProductSummaryRow;
import com.wrb.devica.product.dto.QProductSummaryRow;
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
    public Slice<ProductSummaryRow> findSummariesWithMinPrice(
        LaptopSearchCondition condition, SortType sort, Pageable pageable) {
        int pageSize = pageable.getPageSize();
        List<ProductSummaryRow> found = findSortedPage(condition, sort, pageable.getOffset(), pageSize + 1);
        boolean hasNext = found.size() > pageSize;

        if (hasNext) {
            found = found.subList(0, pageSize);
        }

        return new SliceImpl<>(found, pageable, hasNext);
    }

    private List<ProductSummaryRow> findSortedPage(LaptopSearchCondition condition,
                                                   SortType sort, long offset, int limit) {
        if (isPriceSort(sort)) {
            return findSortedPageByPrice(condition, sort, offset, limit);
        }

        return selectSummary()
            .from(laptop)
            .join(laptop.cpu, cpu)
            .leftJoin(productMinPrice).on(productMinPrice.productId.eq(laptop.id))
            .where(conditions(condition))
            .orderBy(orders(sort))
            .offset(offset)
            .limit(limit)
            .fetch();
    }

    private List<ProductSummaryRow> findSortedPageByPrice(LaptopSearchCondition condition,
                                                          SortType sort, long offset, int limit) {
        List<ProductSummaryRow> priced = selectSummary()
            .from(productMinPrice)
            .join(laptop).on(laptop.id.eq(productMinPrice.productId))
            .join(laptop.cpu, cpu)
            .where(conditions(condition))
            .orderBy(orders(sort))
            .offset(offset)
            .limit(limit)
            .fetch();

        if (priced.size() == limit || hasPriceCondition(condition)) {
            return priced;
        }

        long unpricedOffset = 0;

        if (priced.isEmpty()) {
            unpricedOffset = offset - countPriced(condition);
        }

        List<ProductSummaryRow> unpriced = selectSummary()
            .from(laptop)
            .join(laptop.cpu, cpu)
            .leftJoin(productMinPrice).on(productMinPrice.productId.eq(laptop.id))
            .where(conditions(condition))
            .where(productMinPrice.productId.isNull())
            .orderBy(laptop.id.asc())
            .offset(unpricedOffset)
            .limit(limit - priced.size())
            .fetch();

        List<ProductSummaryRow> found = new ArrayList<>(priced);
        found.addAll(unpriced);
        return found;
    }

    private long countPriced(LaptopSearchCondition condition) {
        return queryFactory
            .select(productMinPrice.count())
            .from(productMinPrice)
            .join(laptop).on(laptop.id.eq(productMinPrice.productId))
            .join(laptop.cpu, cpu)
            .where(conditions(condition))
            .fetchOne();
    }

    private JPAQuery<ProductSummaryRow> selectSummary() {
        return queryFactory.select(new QProductSummaryRow(
            laptop.id,
            laptop.brand,
            laptop.name,
            laptop.imageKey,
            productMinPrice.minPrice,
            laptop.os,
            cpu.name,
            laptop.memoryGb,
            laptop.storageGb
        ));
    }

    private Predicate[] conditions(LaptopSearchCondition condition) {
        return new Predicate[]{
            osEq(condition.os()),
            cpuTierAtLeast(condition.cpuTier()),
            memoryGbGoe(condition.memoryGb()),
            storageGbGoe(condition.storageGb()),
            keywordContains(condition.keyword()),
            brandEq(condition.brand()),
            minPriceGoe(condition.minPrice()),
            minPriceLoe(condition.maxPrice())
        };
    }

    private boolean isPriceSort(SortType sort) {
        return sort == SortType.PRICE_ASC || sort == SortType.PRICE_DESC;
    }

    private boolean hasPriceCondition(LaptopSearchCondition condition) {
        return condition.minPrice() != null || condition.maxPrice() != null;
    }

    // 정렬 값이 같으면 id 순으로 둔다. 가격순은 인덱스를 읽는 방향을 따라야 정렬 없이 인덱스만으로 읽힌다
    private OrderSpecifier<?>[] orders(SortType sort) {
        if (sort == null) {
            return new OrderSpecifier[]{laptop.id.asc()};
        }

        return switch (sort) {
            case RECOMMENDED -> new OrderSpecifier[]{
                cpu.score.desc(), laptop.memoryGb.desc(), laptop.storageGb.desc(), laptop.id.asc()};
            case PRICE_ASC -> new OrderSpecifier[]{productMinPrice.minPrice.asc(), productMinPrice.productId.asc()};
            case PRICE_DESC -> new OrderSpecifier[]{productMinPrice.minPrice.desc(), productMinPrice.productId.desc()};
        };
    }

    @Override
    public List<String> findBrands(Os os) {
        return queryFactory
            .select(laptop.brand)
            .distinct()
            .from(laptop)
            .where(osEq(os))
            .orderBy(laptop.brand.asc())
            .fetch();
    }

    private BooleanExpression minPriceGoe(Long minPrice) {
        if (minPrice == null) {
            return null;
        }
        return productMinPrice.minPrice.goe(minPrice);
    }

    private BooleanExpression minPriceLoe(Long maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return productMinPrice.minPrice.loe(maxPrice);
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
