package com.wrb.devica.product.service;

import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.common.exception.BusinessErrorCode;
import com.wrb.devica.common.exception.BusinessException;
import com.wrb.devica.product.domain.Product;
import com.wrb.devica.product.domain.ProductOffer;
import com.wrb.devica.product.dto.LaptopSearchCondition;
import com.wrb.devica.product.dto.PageCondition;
import com.wrb.devica.product.dto.ProductDetailResponse;
import com.wrb.devica.product.dto.ProductSummaryResponse;
import com.wrb.devica.product.dto.ProductSummaryRow;
import com.wrb.devica.product.repository.LaptopRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class ProductService {

    private final LaptopRepository laptopRepository;
    private final ProductImageUrlResolver imageUrlResolver;
    private final ProductOfferService productOfferService;

    public Slice<ProductSummaryResponse> findProductsByCategory(String categoryCode, LaptopSearchCondition condition,
                                                                PageCondition pageCondition) {
        validateCategoryExists(categoryCode);

        Slice<ProductSummaryRow> rows = laptopRepository.findSummariesWithMinPrice(
            condition,
            pageCondition.sort(),
            PageRequest.of(pageCondition.page(), pageCondition.size())
        );
        return rows.map(row -> ProductSummaryResponse.from(
            row, imageUrlResolver.resolve(row.imageKey()))
        );
    }

    public Product findProductById(Long productId) {
        return laptopRepository.findWithCpuById(productId)
            .orElseThrow(() -> new BusinessException(BusinessErrorCode.LAPTOP_NOT_FOUND));
    }

    public ProductDetailResponse findProductDetailById(Long productId) {
        Product product = findProductById(productId);
        List<ProductOffer> offers =
            productOfferService.findOnSaleOffers(productId);

        return ProductDetailResponse.of(
            product,
            offers,
            imageUrlResolver.resolve(product.getImageKey())
        );
    }

    private void validateCategoryExists(String categoryCode) {
        ProductCategoryCode.from(categoryCode);
    }
}
