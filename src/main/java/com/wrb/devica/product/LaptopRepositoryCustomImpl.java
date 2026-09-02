package com.wrb.devica.product;

import static com.wrb.devica.product.QCpu.cpu;
import static com.wrb.devica.product.QLaptop.laptop;
import static com.wrb.devica.product.QProductOffer.productOffer;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

public class LaptopRepositoryCustomImpl implements LaptopRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public LaptopRepositoryCustomImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Slice<Laptop> findAllByCondition(LaptopSearchCondition condition, Pageable pageable) {
        int pageSize = pageable.getPageSize();

        List<Laptop> found = queryFactory
            .selectFrom(laptop)
            .join(laptop.cpu, cpu).fetchJoin()
            .where(
                onSaleOfferExists(),
                osEq(condition.os()),
                cpuScoreGoe(condition.cpuScore()),
                memoryGbGoe(condition.memoryGb()),
                storageGbGoe(condition.storageGb())
            )
            .orderBy(laptop.id.asc())
            .offset(pageable.getOffset())
            .limit(pageSize + 1L)
            .fetch();

        boolean hasNext = found.size() > pageSize;
        return new SliceImpl<>(hasNext ? found.subList(0, pageSize) : found, pageable, hasNext);
    }

    private BooleanExpression onSaleOfferExists() {
        return JPAExpressions.selectOne()
            .from(productOffer)
            .where(
                productOffer.product.id.eq(laptop.id),
                productOffer.status.eq(OfferStatus.ON_SALE)
            )
            .exists();
    }

    private BooleanExpression osEq(Os os) {
        return os == null ? null : laptop.os.eq(os);
    }

    private BooleanExpression cpuScoreGoe(Integer cpuScore) {
        return cpuScore == null ? null : cpu.score.goe(cpuScore);
    }

    private BooleanExpression memoryGbGoe(Integer memoryGb) {
        return memoryGb == null ? null : laptop.memoryGb.goe(memoryGb);
    }

    private BooleanExpression storageGbGoe(Integer storageGb) {
        return storageGb == null ? null : laptop.storageGb.goe(storageGb);
    }
}
