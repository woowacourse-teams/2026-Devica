package com.wrb.devica.faq;

public record FaqDetailResponse(
    String slug,
    String question,
    String answer
) {

    public static FaqDetailResponse from(Faq faq) {
        return new FaqDetailResponse(faq.getSlug(), faq.getQuestion(), faq.getAnswer());
    }
}
