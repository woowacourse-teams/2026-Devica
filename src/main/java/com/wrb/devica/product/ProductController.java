package com.wrb.devica.product;

import java.util.List;
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
    private final ProductOfferService productOfferService;

    @GetMapping("/api/product-categories/{categoryCode}/products")
    public ResponseEntity<ProductListResponse> findProductsByCategory(
        @PathVariable String categoryCode,
        @Validated @ModelAttribute LaptopSearchCondition condition,
        @Validated @ModelAttribute PageCondition pageCondition
    ) {
        Slice<ProductSummaryResponse> productSlice = productService.findProductsByCategory(categoryCode,
            condition, pageCondition);
        ProductListResponse productListResponse = ProductListResponse.from(productSlice);
        return ResponseEntity.ok().body(productListResponse);
    }

    @GetMapping("/api/products/{id}")
    public ResponseEntity<ProductDetailResponse> findProductById(@PathVariable Long id) {
        Product product = productService.findProductById(id);
        List<ProductOffer> offers = productOfferService.findOnSaleOffers(id);
        return ResponseEntity.ok(ProductDetailResponse.of(product, offers));
    }
}
