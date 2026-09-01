package com.wrb.devica.purpose;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/product-categories/{categoryCode}/usage-purposes")
public class UsagePurposeController {

    private final UsagePurposeService usagePurposeService;

    @GetMapping
    public ResponseEntity<List<UsagePurposeResponse>> findByCategory(@PathVariable String categoryCode) {
        List<UsagePurposeResponse> usagePurposes = usagePurposeService.findByCategoryCode(categoryCode).stream()
            .map(UsagePurposeResponse::from).toList();
        return ResponseEntity.ok().body(usagePurposes);
    }
}
