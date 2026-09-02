package com.wrb.devica.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LaptopRepository extends JpaRepository<Laptop, Long>, JpaSpecificationExecutor<Laptop> {

    /**
     * 전체 개수를 세지 않도록 slice 로 받는다. 정렬은 호출하는 쪽이 정한다.
     */
    default Slice<Laptop> findAllByCondition(LaptopSearchCondition condition, Pageable pageable) {
        return findBy(LaptopSpecification.of(condition), query -> query.slice(pageable));
    }
}
