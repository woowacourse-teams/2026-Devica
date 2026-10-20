package com.wrb.devica.recommendation.dto;

import com.wrb.devica.recommendation.domain.Condition.Selected;
import com.wrb.devica.recommendation.domain.Option;
import com.wrb.devica.recommendation.domain.Question;
import com.wrb.devica.recommendation.domain.Step;
import com.wrb.devica.recommendation.domain.Survey;
import java.util.List;

public record SurveyStepsResponse(List<StepResponse> steps) {

    public static SurveyStepsResponse from(Survey survey) {
        return new SurveyStepsResponse(survey.steps().stream().map(StepResponse::from).toList());
    }

    public record StepResponse(String title, String layout, List<QuestionResponse> questions) {

        private static StepResponse from(Step step) {
            List<QuestionResponse> questions = step.questions().stream().map(QuestionResponse::from).toList();
            return new StepResponse(step.title(), step.layout(), questions);
        }
    }

    public record QuestionResponse(
        String code,
        String title,
        String description,
        String label,
        String input,
        String exclusive,
        ShowWhenResponse showWhen,
        List<OptionResponse> options) {

        private static QuestionResponse from(Question question) {
            return new QuestionResponse(
                question.code(),
                question.title(),
                question.description(),
                question.label(),
                question.input().name(),
                question.exclusive(),
                ShowWhenResponse.from(question.showWhen()),
                question.options().stream().map(OptionResponse::from).toList());
        }
    }

    public record ShowWhenResponse(String question, List<String> anyOf) {

        private static ShowWhenResponse from(Selected showWhen) {
            if (showWhen == null) {
                return null;
            }
            return new ShowWhenResponse(showWhen.question(), showWhen.anyOf());
        }
    }

    public record OptionResponse(String code, String content, String description) {

        private static OptionResponse from(Option option) {
            return new OptionResponse(option.code(), option.content(), option.description());
        }
    }
}
