package com.wrb.devica.recommendation.judgement;

import com.wrb.devica.recommendation.survey.Condition;

public record Rule(String criterion, int weight, Condition when, String reason) {
}
