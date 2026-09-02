package com.wrb.devica.recommendation;

import com.wrb.devica.purpose.UsagePurposeCode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {

    public List<RecommendedSpec> findByPurposeCode(String purposeCode) {
        UsagePurposeCode purpose = UsagePurposeCode.from(purposeCode);
        return LaptopRecommendation.findByPurpose(purpose);
    }
}
