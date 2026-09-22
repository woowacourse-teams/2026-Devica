package com.wrb.devica.question.domain.option;

import com.wrb.devica.question.domain.OptionCode;

public enum Slowdown implements OptionCode {

    OFTEN,      // 자주 그렇다
    SOMETIMES,  // 가끔 느려진다
    FINE,       // 괜찮다
    UNKNOWN     // 잘 모르겠다
}
