package com.wrb.devica.question;

/**
 * 특정 질문이 노출되는 조건을 표현한다.
 * 선택지만으로는 어느 질문의 답인지 알 수 없어 질문과 선택지를 함께 가진다.
 */
public record QuestionDependency(QuestionCode question, OptionCode option) {
}
