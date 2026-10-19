package com.wrb.devica.recommendation.survey;

import java.util.List;

public record Step(String title, String layout, List<Question> questions) {
}
