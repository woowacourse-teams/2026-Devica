package com.wrb.devica.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LaptopRepository extends JpaRepository<Laptop, Long>, JpaSpecificationExecutor<Laptop> {

    default Slice<Laptop> findAllByCondition(LaptopSearchCondition condition, Pageable pageable) {
        return findBy(
            LaptopSpecification.of(condition),
            query -> query.slice(pageable)
        );
    }
}
