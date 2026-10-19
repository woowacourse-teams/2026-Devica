package com.wrb.devica.recommendation.domain;


public record Rule(String criterion, int weight, Condition when, String reason) {
}
