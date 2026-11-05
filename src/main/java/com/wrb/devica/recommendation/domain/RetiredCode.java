package com.wrb.devica.recommendation.domain;

import java.time.LocalDate;

public record RetiredCode(String question, String option,
                          String to, Boolean drop, Boolean dropInvalid, String was, LocalDate retiredAt) {

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

    boolean isFor(String option) {
        if (option == null) {
            return isQuestionLine();
        }

        return option.equals(this.option);
    }

    String where() {
        if (isOptionLine()) {
            return "지운 코드 " + question + "." + option;
        }

        return "지운 코드 " + question;
    }
}
