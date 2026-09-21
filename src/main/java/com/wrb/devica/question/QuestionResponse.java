package com.wrb.devica.question;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record QuestionResponse(
    String code,
    String title,
    String description,
    String inputType,
    DependencyResponse dependsOn,
    List<OptionResponse> options) {

    public static QuestionResponse from(Question question) {
        QuestionCode code = question.getCode();
        return new QuestionResponse(
            code.name(),
            question.getTitle(),
            question.getDescription(),
            code.getInputType().name(),
            DependencyResponse.from(code.getDependency()),
            OptionResponse.allOf(code, question.getOptions()));
    }

    public record DependencyResponse(String questionCode, String optionCode) {

        private static DependencyResponse from(QuestionDependency dependency) {
            if (dependency == null) {
                return null;
            }
            return new DependencyResponse(dependency.question().name(), dependency.option().name());
        }
    }

    public record OptionResponse(String code, String content, String description, boolean exclusive) {

        /**
         * 순서와 배타 여부는 QuestionCode 가 갖고, DB 행은 문구만 댄다.
         */
        private static List<OptionResponse> allOf(QuestionCode questionCode, List<QuestionOption> options) {
            Map<String, QuestionOption> contents = options.stream()
                .collect(Collectors.toMap(QuestionOption::getCode, Function.identity()));
            return questionCode.getOptions().stream()
                .map(code -> of(code, contents.get(code.name()), code == questionCode.getExclusiveOption()))
                .toList();
        }

        private static OptionResponse of(OptionCode code, QuestionOption option, boolean exclusive) {
            return new OptionResponse(code.name(), option.getContent(), option.getDescription(), exclusive);
        }
    }
}
