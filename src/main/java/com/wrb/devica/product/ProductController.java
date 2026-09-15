package com.wrb.devica.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ProductController {

    private final ProductService productService;

    @GetMapping("/api/usage-purposes/{purposeCode}/products")
    public ResponseEntity<ProductListResponse> findProducts(
        @PathVariable String purposeCode,
        @Validated @ModelAttribute LaptopSearchCondition condition,
        @Validated @ModelAttribute PageCondition pageCondition
    ) {
        Slice<ProductSummaryResponse> productSlice = productService.findProducts(purposeCode,
            condition, pageCondition);
        ProductListResponse productListResponse = ProductListResponse.from(productSlice);
        return ResponseEntity.ok().body(productListResponse);
    }

    @GetMapping("/api/products/{id}")
    public ResponseEntity<ProductDetailResponse> findProductById(@PathVariable Long id) {
        ProductDetailResponse productDetailResponse = productService.findProductById(id);
        return ResponseEntity.ok(productDetailResponse);
    }
}
