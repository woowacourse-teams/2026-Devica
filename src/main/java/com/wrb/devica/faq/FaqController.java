package com.wrb.devica.faq;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class FaqController {

    private final FaqService faqService;

    @GetMapping("/faqs")
    public ResponseEntity<List<FaqSummaryResponse>> findServiceFaqs() {
        List<FaqSummaryResponse> faqs = faqService.findPublishedServiceFaqs().stream()
            .map(FaqSummaryResponse::from)
            .toList();
        return ResponseEntity.ok().body(faqs);
    }

    @GetMapping("/faqs/{slug}")
    public ResponseEntity<FaqDetailResponse> findFaqBySlug(@PathVariable String slug) {
        FaqDetailResponse faqDetailResponse = FaqDetailResponse.from(faqService.findPublishedFaqBySlug(slug));
        return ResponseEntity.ok().body(faqDetailResponse);
    }

    @GetMapping("/product-categories/{categoryCode}/usage-purposes/{purposeCode}/faqs")
    public ResponseEntity<List<FaqSummaryResponse>> findFaqsByUsagePurpose(
        @PathVariable String categoryCode,
        @PathVariable String purposeCode
    ) {
        List<FaqSummaryResponse> faqs = faqService.findPublishedFaqsBy(categoryCode, purposeCode).stream()
            .map(FaqSummaryResponse::from)
            .toList();
        return ResponseEntity.ok().body(faqs);
    }
}
