package com.wrb.devica.recommendation.judgement;

import com.wrb.devica.recommendation.survey.Condition;

public record ConditionMapping(String criterion, String value, Condition when) {
}
