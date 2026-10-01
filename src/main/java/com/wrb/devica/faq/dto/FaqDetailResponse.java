package com.wrb.devica.faq.dto;

import com.wrb.devica.faq.domain.Faq;

public record FaqDetailResponse(
    String slug,
    String question,
    String answer
) {

    public static FaqDetailResponse from(Faq faq) {
        return new FaqDetailResponse(faq.getSlug(), faq.getTitle(), faq.getContent());
    }
}
