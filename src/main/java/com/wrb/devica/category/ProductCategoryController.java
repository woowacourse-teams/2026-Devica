package com.wrb.devica.category;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/product-categories")
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    @GetMapping
    public List<ProductCategoryResponse> findAll() {
        return productCategoryService.findAll().stream()
                .map(ProductCategoryResponse::from)
                .toList();
    }
}
