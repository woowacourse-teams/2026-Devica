package com.wrb.devica.recommendation.domain;

import java.util.List;

public record Step(String title, String layout, List<Question> questions) {
}
