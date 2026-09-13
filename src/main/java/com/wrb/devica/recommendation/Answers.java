package com.wrb.devica.recommendation;

import com.wrb.devica.question.OptionCode;
import com.wrb.devica.question.QuestionCode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 사용자가 고른 선택지. 건너뛴 질문은 키가 없다.
 * <p>
 * 알고리즘은 자기가 보는 질문만 꺼내 쓴다. 현재 사양 입력도 일반 질문의 답변이라 같이 담긴다.
 */
public record Answers(Map<QuestionCode, List<OptionCode>> selected) {

    private static final Answers EMPTY = new Answers(Map.of());

    public Answers {
        selected = Map.copyOf(selected);
    }

    public static Answers empty() {
        return EMPTY;
    }

    /**
     * 단일 선택 질문의 답. 건너뛰었으면 null 이다.
     */
    public OptionCode single(QuestionCode question) {
        List<OptionCode> options = selected.getOrDefault(question, List.of());
        return options.isEmpty() ? null : options.getFirst();
    }

    public boolean has(QuestionCode question, OptionCode option) {
        return selected.getOrDefault(question, List.of()).contains(option);
    }

    public boolean hasAnyOf(QuestionCode question, OptionCode... options) {
        return Arrays.stream(options).anyMatch(option -> has(question, option));
    }
}
