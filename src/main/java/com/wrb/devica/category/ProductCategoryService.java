package com.wrb.devica.category;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductCategoryService {

    public List<ProductCategoryCode> findAll() {
        return List.of(ProductCategoryCode.values());
    }
}
