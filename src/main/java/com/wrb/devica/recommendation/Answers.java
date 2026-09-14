package com.wrb.devica.recommendation;

import com.wrb.devica.question.OptionCode;
import com.wrb.devica.question.QuestionCode;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자가 고른 선택지. 건너뛴 질문은 키가 없다.
 */
public record Answers(Map<QuestionCode, List<OptionCode>> selected) {

    public Answers {
        selected = Map.copyOf(selected);
    }

    /**
     * 요청이 보낸 코드 문자열을 해석한다. 선택지는 질문마다 타입이 달라 질문이 해석하고,
     * 고를 수 없는 조합이면 예외를 던진다.
     */
    public static Answers from(Map<String, List<String>> answers) {
        Map<QuestionCode, List<OptionCode>> selected = new EnumMap<>(QuestionCode.class);
        answers.forEach((code, optionCodes) -> {
            QuestionCode question = QuestionCode.from(code);
            selected.put(question, question.select(optionCodes));
        });
        return new Answers(selected);
    }

    public static Answers empty() {
        return new Answers(Map.of());
    }

    public boolean isAnswered(QuestionCode question) {
        return !optionsOf(question).isEmpty();
    }

    /**
     * 단일 선택 질문의 답. 건너뛰었으면 null 이다. 여러 개를 고를 수 있는 질문에는 쓰지 않는다.
     */
    public OptionCode answerTo(QuestionCode question) {
        List<OptionCode> options = optionsOf(question);
        if (options.isEmpty()) {
            return null;
        }
        return options.getFirst();
    }

    public boolean has(QuestionCode question, OptionCode option) {
        return optionsOf(question).contains(option);
    }

    public boolean hasAnyOf(QuestionCode question, OptionCode... options) {
        return Arrays.stream(options).anyMatch(option -> has(question, option));
    }

    private List<OptionCode> optionsOf(QuestionCode question) {
        return selected.getOrDefault(question, List.of());
    }
}
