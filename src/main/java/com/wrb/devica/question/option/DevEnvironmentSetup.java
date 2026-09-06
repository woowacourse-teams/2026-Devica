package com.wrb.devica.question.option;

import com.wrb.devica.question.OptionCode;

public enum DevEnvironmentSetup implements OptionCode {

    LOCAL_MANY,     // 로컬에 직접 설치해서 여러 개 띄운다
    LOCAL_FEW,      // 로컬에 직접 설치해서 한두 개 띄운다
    DOCKER_MANY,    // 도커로 여러 개를 한 번에 띄운다
    DOCKER_FEW,     // 도커로 한두 개 띄운다
    REMOTE,         // 원격 서버에 접속해서 쓴다
    NO_EXPERIENCE   // 아직 다뤄본 적이 없다
}
