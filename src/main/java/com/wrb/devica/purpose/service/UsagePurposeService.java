package com.wrb.devica.purpose.service;

import com.wrb.devica.category.domain.ProductCategoryCode;
import com.wrb.devica.purpose.domain.UsagePurposeCode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UsagePurposeService {

    public List<UsagePurposeCode> findByCategoryCode(String categoryCode) {
        return UsagePurposeCode.findByCategory(ProductCategoryCode.from(categoryCode));
    }
}
