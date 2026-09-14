package com.wrb.devica.recommendation;

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

    /**
     * 답변을 파라미터로 받아 조정한 권장 사양. 답변이 없으면 조정 전 기본 권장 사양이다.
     * 답변 유무는 같은 자원의 다른 상태일 뿐이라 경로도 메서드도 나누지 않는다.
     */
    @GetMapping
    public ResponseEntity<RecommendationResponse> recommend(
        @PathVariable String purposeCode,
        @RequestParam MultiValueMap<String, String> answers
    ) {
        List<RecommendedSpec> recommendedSpecs = recommendationService.recommend(purposeCode, answers);
        return ResponseEntity.ok().body(RecommendationResponse.from(recommendedSpecs));
    }
}
