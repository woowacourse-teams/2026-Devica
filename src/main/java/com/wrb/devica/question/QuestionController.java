package com.wrb.devica.question;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/usage-purposes/{purposeCode}/questions")
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping
    public ResponseEntity<List<QuestionResponse>> findByPurpose(@PathVariable String purposeCode) {
        List<QuestionResponse> questions = questionService.findByPurposeCode(purposeCode).stream()
            .map(QuestionResponse::from).toList();
        return ResponseEntity.ok().body(questions);
    }
}
