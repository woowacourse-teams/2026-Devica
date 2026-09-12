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
@RequestMapping("/api/laptops")
public class LaptopController {

    private final LaptopService laptopService;

    @GetMapping
    public ResponseEntity<LaptopListResponse> findLaptops(
        @Validated @ModelAttribute LaptopSearchCondition condition,
        @Validated @ModelAttribute PageCondition pageCondition
    ) {
        Slice<LaptopSummaryResponse> laptopSlice = laptopService.findLaptops(condition, pageCondition);
        LaptopListResponse laptopListResponse = LaptopListResponse.from(laptopSlice);
        return ResponseEntity.ok().body(laptopListResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LaptopDetailResponse> findLaptopById(@PathVariable Long id) {
        return ResponseEntity.ok(laptopService.findLaptopById(id));
    }
}
