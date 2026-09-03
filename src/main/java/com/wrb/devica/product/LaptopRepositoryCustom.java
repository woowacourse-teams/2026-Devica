package com.wrb.devica.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface LaptopRepositoryCustom {

    Slice<Laptop> findAllByCondition(LaptopSearchCondition condition, Pageable pageable);
}
