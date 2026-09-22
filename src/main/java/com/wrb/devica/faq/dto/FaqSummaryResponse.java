package com.wrb.devica.faq.dto;

import com.wrb.devica.faq.domain.Faq;

public record FaqSummaryResponse(
    String slug,
    String question
) {

    public static FaqSummaryResponse from(Faq faq) {
        return new FaqSummaryResponse(faq.getSlug(), faq.getTitle());
    }
}
