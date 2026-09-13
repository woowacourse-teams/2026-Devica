package com.wrb.devica.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/usage-purposes/{purposeCode}/products")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ProductListResponse> findProducts(
        @PathVariable String purposeCode,
        @Validated @ModelAttribute LaptopSearchCondition condition,
        @Validated @ModelAttribute LaptopPageCondition pageCondition
    ) {
        Slice<ProductSummaryResponse> productSlice = productService.findProducts(purposeCode,
            condition, pageCondition);
        ProductListResponse productListResponse = ProductListResponse.from(productSlice);
        return ResponseEntity.ok().body(productListResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> findProductById(
        @PathVariable String purposeCode,
        @PathVariable Long id
    ) {
        ProductDetailResponse productDetailResponse = productService.findProductById(purposeCode, id);
        return ResponseEntity.ok(productDetailResponse);
    }
}
