package com.zimuzeng.outfitapp.common.exception;

/**
 * JSON body returned for every error response: the {@link ErrorCode} name and its message.
 * The HTTP status itself conveys the status; it is intentionally not duplicated here.
 */
public record ErrorResponse(String code, String message) {
}
