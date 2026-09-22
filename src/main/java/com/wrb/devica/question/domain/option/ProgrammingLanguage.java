package com.wrb.devica.question.domain.option;

import com.wrb.devica.question.domain.OptionCode;

public enum ProgrammingLanguage implements OptionCode {

    JAVA_FAMILY,        // Java, Kotlin, C# 등
    NODE_TYPESCRIPT,    // Node.js, TypeScript
    OTHERS,             // Python, Go, PHP 등
    UNDECIDED           // 아직 정하지 않았다 (배타)
}
