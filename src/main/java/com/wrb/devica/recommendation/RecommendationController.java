package com.wrb.devica.recommendation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/usage-purposes/{purposeCode}/recommendation")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<RecommendationResponse> findByPurpose(@PathVariable String purposeCode) {
        List<RecommendedSpec> recommendedSpecs = recommendationService.findByPurposeCode(purposeCode);
        return ResponseEntity.ok().body(RecommendationResponse.from(recommendedSpecs));
    }
}
