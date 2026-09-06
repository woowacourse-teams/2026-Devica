package com.wrb.devica.question;

/**
 * 질문별 선택지 enum 이 구현한다. 질문마다 타입이 달라, 다른 질문의 선택지와 비교하면 컴파일 에러가 난다.
 */
public interface OptionCode {

    String name();
}
