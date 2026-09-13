package com.wrb.devica.recommendation;

import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationAlgorithms algorithms;

    public List<RecommendedSpec> findByPurposeCode(String purposeCode) {
        UsagePurposeCode purpose = UsagePurposeCode.from(purposeCode);
        return algorithms.findByPurpose(purpose).recommend();
    }
}
