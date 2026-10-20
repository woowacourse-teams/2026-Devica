package com.wrb.devica.recommendation.controller;

import com.wrb.devica.recommendation.domain.RecommendedSpec;
import com.wrb.devica.recommendation.dto.RecommendationResponse;
import com.wrb.devica.recommendation.service.RecommendationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/usage-purposes/{purposeCode}/recommendation")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<RecommendationResponse> recommend(
        @PathVariable String purposeCode,
        @RequestParam MultiValueMap<String, String> answers
    ) {
        List<RecommendedSpec> recommendedSpecs = recommendationService.recommend(purposeCode, answers);
        return ResponseEntity.ok().body(RecommendationResponse.from(recommendedSpecs));
    }
}
