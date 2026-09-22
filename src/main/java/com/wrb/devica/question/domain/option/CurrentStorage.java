package com.wrb.devica.question.domain.option;

import com.wrb.devica.question.domain.OptionCode;

public enum CurrentStorage implements OptionCode {

    GB_256_OR_LESS, // 256GB 이하
    UNDER_1TB,      // 256GB 초과 1TB 미만
    TB_1_OR_MORE    // 1TB 이상
}
