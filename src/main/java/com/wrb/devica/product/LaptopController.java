package com.wrb.devica.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/laptops")
public class LaptopController {

    private final LaptopService laptopService;

    @GetMapping
    public ResponseEntity<LaptopListResponse> findLaptops(
        @Validated @ModelAttribute LaptopSearchCondition condition,
        @RequestParam(defaultValue = "0") @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다.") int page,
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size
    ) {
        Slice<LaptopSummaryResponse> laptopSlice = laptopService.findLaptops(condition, page, size);
        LaptopListResponse laptopListResponse = LaptopListResponse.of(laptopSlice);
        return ResponseEntity.ok().body(laptopListResponse);
    }
}
