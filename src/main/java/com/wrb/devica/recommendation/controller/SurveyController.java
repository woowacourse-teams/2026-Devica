package com.wrb.devica.recommendation.controller;

import com.wrb.devica.recommendation.domain.Survey;
import com.wrb.devica.recommendation.dto.SurveyStepsResponse;
import com.wrb.devica.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/usage-purposes/{purposeCode}/survey")
public class SurveyController {

    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<SurveyStepsResponse> findSurvey(@PathVariable String purposeCode) {
        Survey survey = recommendationService.findSurvey(purposeCode);
        SurveyStepsResponse surveyStepsResponse = SurveyStepsResponse.from(survey);
        return ResponseEntity.ok().body(surveyStepsResponse);
    }
}
