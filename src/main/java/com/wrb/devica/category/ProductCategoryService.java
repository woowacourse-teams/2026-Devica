package com.wrb.devica.category;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCategoryService {

    public List<ProductCategoryName> findAll() {
        return List.of(ProductCategoryName.values());
    }
}
