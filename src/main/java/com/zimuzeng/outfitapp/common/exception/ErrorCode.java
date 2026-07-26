package com.zimuzeng.outfitapp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Central registry of every error the API can return. Each code pairs an HTTP status with a
 * message template ({@link String#format} placeholders) so that {@link AppException} and
 * {@link GlobalExceptionHandler} never need to know the specifics of any individual error.
 */
public enum ErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "A user with email '%s' already exists"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User with id '%s' not found"),
    UPLOAD_BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "Upload batch with id '%s' not found"),
    UPLOAD_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Upload item with id '%s' not found"),
    GARMENT_EXTRACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Garment extraction for upload item '%s' not found"),
    GARMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Garment with id '%s' not found"),
    NO_ELIGIBLE_GARMENTS(HttpStatus.UNPROCESSABLE_ENTITY,
            "No garments with completed metadata found for user '%s' - upload and process some garments first"),
    BUY_ADVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Buy advice with id '%s' not found"),
    UNSUPPORTED_IMAGE_FORMAT(HttpStatus.BAD_REQUEST, "Cannot decode image of content type '%s': unsupported by ImageIO"),
    QWEN_RESPONSE_PARSE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse Qwen response: %s"),
    UNSUPPORTED_LANG(HttpStatus.BAD_REQUEST, "Unsupported lang '%s'; supported values are 'en' and 'zh'"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed: %s"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus httpStatus;
    private final String messageTemplate;

    ErrorCode(HttpStatus httpStatus, String messageTemplate) {
        this.httpStatus = httpStatus;
        this.messageTemplate = messageTemplate;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String formatMessage(Object... args) {
        return args.length == 0 ? messageTemplate : String.format(messageTemplate, args);
    }
}
