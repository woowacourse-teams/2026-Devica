package com.wrb.devica.common;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    String getCode();

    HttpStatus getStatus();

    String getMessage();
}
