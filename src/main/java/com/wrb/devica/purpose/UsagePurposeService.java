package com.wrb.devica.purpose;

import com.wrb.devica.category.ProductCategoryCode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UsagePurposeService {

    public List<UsagePurposeCode> findByCategoryCode(String categoryCode) {
        return UsagePurposeCode.findByCategory(ProductCategoryCode.from(categoryCode));
    }
}
