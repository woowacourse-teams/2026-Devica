package com.wrb.devica.common.exception;

import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.atWarn()
            .addKeyValue("error.code", errorCode.getCode())
            .log("비즈니스 예외가 발생");
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        log.error("처리하지 못한 예외가 발생", exception);
        ErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception, HttpHeaders headers,
        HttpStatusCode status, WebRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .filter(fieldError -> !fieldError.isBindingFailure())
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .collect(Collectors.joining(" "));

        return toResponse(exception, status, message);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
        HandlerMethodValidationException exception, HttpHeaders headers,
        HttpStatusCode status, WebRequest request) {
        String message = exception.getParameterValidationResults().stream()
            .flatMap(result -> result.getResolvableErrors().stream())
            .map(MessageSourceResolvable::getDefaultMessage)
            .collect(Collectors.joining(" "));

        return toResponse(exception, status, message);
    }

    private ResponseEntity<Object> toResponse(Exception exception, HttpStatusCode status, String message) {
        CommonErrorCode errorCode = CommonErrorCode.from(status);

        if (message.isBlank()) {
            message = errorCode.getMessage();
        }
        // 사유는 사용자 입력이 아니라 제약 조건에 적어 둔 문구라 error.message 로 남겨도 된다.
        log.atWarn()
            .addKeyValue("error.type", exception.getClass().getName())
            .addKeyValue("error.message", message)
            .log("요청 값 검증 실패");

        return ResponseEntity.status(status).body(ErrorResponse.of(errorCode, message));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception exception, Object body, HttpHeaders headers,
        HttpStatusCode status, WebRequest request) {
        // 4xx 는 클라이언트 요청 문제라 스택 트레이스 없이 WARN 으로 남긴다.
        // 예외 메시지에는 요청 값이 섞이므로 종류만 남긴다.
        // 없는 경로(404)는 봇 스캔이 대부분이라 남기지 않고, 요청 로그의 상태 코드로 본다.
        if (status.is5xxServerError()) {
            log.error("처리하지 못한 예외가 발생", exception);
        } else if (!(exception instanceof NoResourceFoundException)) {
            log.atWarn()
                .addKeyValue("error.type", exception.getClass().getName())
                .log("잘못된 요청");
        }
        return ResponseEntity.status(status)
            .body(ErrorResponse.from(CommonErrorCode.from(status)));
    }
}
