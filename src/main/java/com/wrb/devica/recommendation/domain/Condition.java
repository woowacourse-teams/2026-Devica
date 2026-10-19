package com.wrb.devica.recommendation.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.stream.Stream;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
    @JsonSubTypes.Type(Condition.Selected.class),
    @JsonSubTypes.Type(Condition.All.class),
    @JsonSubTypes.Type(Condition.Any.class),
    @JsonSubTypes.Type(Condition.Not.class)})
public sealed interface Condition {

    boolean evaluate(SurveyResponse response);

    Stream<Selected> selections();

    record Selected(String question, List<String> anyOf) implements Condition {

        @Override
        public boolean evaluate(SurveyResponse response) {
            return anyOf.stream().anyMatch(option -> response.has(question, option));
        }

        @Override
        public Stream<Selected> selections() {
            return Stream.of(this);
        }
    }

    record All(List<Condition> all) implements Condition {

        @Override
        public boolean evaluate(SurveyResponse response) {
            return all.stream().allMatch(condition -> condition.evaluate(response));
        }

        @Override
        public Stream<Selected> selections() {
            return all.stream().flatMap(Condition::selections);
        }
    }

    record Any(List<Condition> any) implements Condition {

        @Override
        public boolean evaluate(SurveyResponse response) {
            return any.stream().anyMatch(condition -> condition.evaluate(response));
        }

        @Override
        public Stream<Selected> selections() {
            return any.stream().flatMap(Condition::selections);
        }
    }

    record Not(Condition not) implements Condition {

        @Override
        public boolean evaluate(SurveyResponse response) {
            return !not.evaluate(response);
        }

        @Override
        public Stream<Selected> selections() {
            return not.selections();
        }
    }
}
