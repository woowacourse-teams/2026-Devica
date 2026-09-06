package com.wrb.devica.question.option;

import com.wrb.devica.question.OptionCode;

public enum AiCodingTool implements OptionCode {

    AI_EDITOR,      // Cursor 같은 AI 전용 에디터를 쓴다
    IDE_EXTENSION,  // 기존 IDE 에 확장으로 붙여 쓴다
    NONE            // 안 쓴다
}
