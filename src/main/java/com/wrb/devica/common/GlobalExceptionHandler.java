package com.wrb.devica.common;

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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        log.error("처리하지 못한 예외가 발생", exception);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
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
        return toResponse(status, message);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
        HandlerMethodValidationException exception, HttpHeaders headers,
        HttpStatusCode status, WebRequest request) {
        String message = exception.getParameterValidationResults().stream()
            .flatMap(result -> result.getResolvableErrors().stream())
            .map(MessageSourceResolvable::getDefaultMessage)
            .collect(Collectors.joining(" "));
        return toResponse(status, message);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception exception, Object body, HttpHeaders headers,
        HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.from(ErrorCode.from(status)));
    }

    private ResponseEntity<Object> toResponse(HttpStatusCode status, String message) {
        ErrorCode errorCode = ErrorCode.from(status);
        String body = message.isBlank() ? errorCode.getMessage() : message;
        return ResponseEntity.status(status).body(new ErrorResponse(body, errorCode.name()));
    }
}
