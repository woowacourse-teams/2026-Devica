package com.wrb.devica.recommendation;

import com.wrb.devica.question.OptionCode;
import com.wrb.devica.question.QuestionCode;
import com.wrb.devica.question.QuestionDependency;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 사용자가 고른 선택지. 건너뛴 질문은 키가 없다.
 * <p>
 * 의존이 충족되지 않은 답은 담지 않는다 — 선행 답이 조건과 맞지 않으면 화면에 나오지 않는 질문이라
 * 사용자가 답한 적 없는 값이다. 답을 읽는 곳마다 그 전제를 다시 확인하지 않게 여기서 한 번 걸러낸다.
 * 의존은 한 단계뿐이라 연쇄는 보지 않는다.
 */
public record Answers(Map<QuestionCode, List<OptionCode>> selected) {

    public Answers {
        selected = Map.copyOf(withoutUnmetDependencies(selected));
    }

    private static Map<QuestionCode, List<OptionCode>> withoutUnmetDependencies(
        Map<QuestionCode, List<OptionCode>> selected) {
        return selected.entrySet().stream()
            .filter(entry -> dependencyMet(entry.getKey(), selected))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static boolean dependencyMet(QuestionCode question,
                                         Map<QuestionCode, List<OptionCode>> selected) {
        QuestionDependency dependency = question.getDependency();
        if (dependency == null) {
            return true;
        }
        return selected.getOrDefault(dependency.question(), List.of()).contains(dependency.option());
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
