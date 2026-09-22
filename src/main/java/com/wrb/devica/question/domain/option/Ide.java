package com.wrb.devica.question.domain.option;

import com.wrb.devica.question.domain.OptionCode;

public enum Ide implements OptionCode {

    JETBRAINS,      // IntelliJ, PyCharm 등 JetBrains 제품
    LIGHT_EDITOR,   // VS Code, Vim, Neovim 등
    MULTIPLE,       // 여러 개를 동시에 함께 쓴다
    UNKNOWN         // 아직 모르겠다
}
