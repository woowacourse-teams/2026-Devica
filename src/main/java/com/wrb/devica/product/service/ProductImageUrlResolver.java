package com.wrb.devica.product.service;

import static org.springframework.util.StringUtils.hasText;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProductImageUrlResolver {

    private final String baseUrl;

    public ProductImageUrlResolver(@Value("${app.product-image-base-url}") String baseUrl) {
        if (!hasText(baseUrl)) {
            throw new IllegalArgumentException("이미지 기본 주소가 필요합니다.");
        }

        int end = baseUrl.length();
        while (end > 0 && baseUrl.charAt(end - 1) == '/') {
            end--;
        }
        this.baseUrl = baseUrl.substring(0, end);
    }

    public String resolve(String imageKey) {
        if (!hasText(imageKey)) {
            return null;
        }

        int start = 0;
        while (start < imageKey.length() && imageKey.charAt(start) == '/') {
            start++;
        }
        return baseUrl + "/" + imageKey.substring(start);
    }
}
