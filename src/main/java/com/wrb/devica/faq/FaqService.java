package com.wrb.devica.faq;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    public List<Faq> findPublishedHomeFaqs() {
        return faqRepository.findAllByUsagePurposeIsNullAndPublishedTrueOrderByDisplayOrderAsc();
    }
}
