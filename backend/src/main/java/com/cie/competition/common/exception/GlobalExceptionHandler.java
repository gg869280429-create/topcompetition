package com.cie.competition.common.exception;

import com.cie.competition.common.result.Result;
import com.cie.competition.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，统一将异常转换为 Result 响应结构。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "文件不能超过10MB");
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(jakarta.validation.ConstraintViolationException e) {
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "请求参数不合法");
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? ResultCode.BAD_REQUEST.getMessage() : fieldError.getDefaultMessage();
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.fail(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.ERROR.getCode(), ResultCode.ERROR.getMessage());
    }
}
