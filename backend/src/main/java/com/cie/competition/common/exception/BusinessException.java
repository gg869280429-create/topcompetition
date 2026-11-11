package com.cie.competition.common.exception;

import com.cie.competition.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常，用于业务规则校验失败时抛出，由全局异常处理器统一转换为响应结果。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BAD_REQUEST.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }
}
