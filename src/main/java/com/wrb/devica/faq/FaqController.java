package com.wrb.devica.faq;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/home/faqs")
public class FaqController {

    private final FaqService faqService;

    @GetMapping
    public ResponseEntity<List<FaqSummaryResponse>> findHomeFaqs() {
        List<FaqSummaryResponse> faqs = faqService.findPublishedHomeFaqs().stream()
            .map(FaqSummaryResponse::from)
            .toList();
        return ResponseEntity.ok().body(faqs);
    }
}
