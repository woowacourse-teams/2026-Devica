package com.wrb.devica.product;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class LaptopSpecification {

    private static final String CPU = "cpu";
    private static final String OS = "os";
    private static final String SCORE = "score";
    private static final String MEMORY_GB = "memoryGb";
    private static final String STORAGE_GB = "storageGb";

    private LaptopSpecification() {
    }

    /**
     * 값이 있는 조건만 최소 기준(>=)으로 더한다. 비어 있으면 조건 없이 전체를 조회한다.
     */
    public static Specification<Laptop> of(LaptopSearchCondition condition) {
        return (laptop, query, builder) -> {
            // 목록 응답에 CPU 이름과 코어 수가 들어간다. fetch 하지 않으면 조회한 노트북 수만큼 CPU 를 다시 읽는다.
            // 조건에도 써야 하므로 Join 으로 받아 재사용한다.
            Join<Laptop, Cpu> cpu = (Join<Laptop, Cpu>) (Fetch<?, ?>) laptop.fetch(CPU);

            List<Predicate> predicates = new ArrayList<>();
            // 판매 중인 오퍼가 하나도 없는 제품은 목록에서 뺀다.
            predicates.add(builder.exists(onSaleOffer(laptop, query, builder)));

            if (condition.os() != null) {
                predicates.add(builder.equal(laptop.get(OS), condition.os()));
            }
            if (condition.cpuScore() != null) {
                predicates.add(builder.greaterThanOrEqualTo(cpu.get(SCORE), condition.cpuScore()));
            }
            if (condition.memoryGb() != null) {
                predicates.add(builder.greaterThanOrEqualTo(laptop.get(MEMORY_GB), condition.memoryGb()));
            }
            if (condition.storageGb() != null) {
                predicates.add(builder.greaterThanOrEqualTo(laptop.get(STORAGE_GB), condition.storageGb()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Subquery<Long> onSaleOffer(Root<Laptop> laptop, CriteriaQuery<?> query,
                                              CriteriaBuilder builder) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<ProductOffer> offer = subquery.from(ProductOffer.class);
        return subquery.select(offer.get("id"))
            .where(
                builder.equal(offer.get("product"), laptop),
                builder.equal(offer.get("status"), OfferStatus.ON_SALE)
            );
    }
}
