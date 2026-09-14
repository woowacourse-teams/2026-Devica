package com.wrb.devica.recommendation;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // 답변이 없는 상태의 추천인 GET 과 같은 자원이라 경로를 나누지 않는다
    @PostMapping
    public ResponseEntity<RecommendationResponse> recommendByAnswers(
        @PathVariable String purposeCode,
        @Valid @RequestBody RecommendationRequest request
    ) {
        List<RecommendedSpec> recommendedSpecs =
            recommendationService.recommendByAnswers(purposeCode, request);
        return ResponseEntity.ok().body(RecommendationResponse.from(recommendedSpecs));
    }
}
