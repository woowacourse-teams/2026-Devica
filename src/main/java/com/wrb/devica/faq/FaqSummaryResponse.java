package com.wrb.devica.faq;

public record FaqSummaryResponse(
    String slug,
    String question
) {

    public static FaqSummaryResponse from(Faq faq) {
        return new FaqSummaryResponse(faq.getSlug(), faq.getQuestion());
    }
}
