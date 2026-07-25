package com.zimuzeng.outfitapp.common.exception;

/**
 * The single exception type used across the application for any error that should be reported
 * to API clients as a coded, structured response. The {@link ErrorCode} carries the HTTP status
 * and message template; callers only supply the dynamic values referenced by that template.
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.formatMessage(args), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
