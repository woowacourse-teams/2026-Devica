package com.wrb.devica.recommendation.domain;

public record RetiredCode(String question, String option, String to, Boolean drop, Boolean dropInvalid, String was) {

    public RetiredCode {
        drop = Boolean.TRUE.equals(drop);
        dropInvalid = Boolean.TRUE.equals(dropInvalid);
    }

    boolean isQuestionLine() {
        return option == null && !dropInvalid;
    }

    boolean isOptionLine() {
        return option != null;
    }
}
