package com.airtribe.taskmanager.api;

import com.airtribe.taskmanager.exception.ApiException;
import com.airtribe.taskmanager.exception.BadGatewayException;
import com.airtribe.taskmanager.exception.ValidationException;
import com.airtribe.taskmanager.model.ApiResponse;
import com.airtribe.taskmanager.model.ErrorDto;

public class ErrorResponseFactory {

    private ErrorResponseFactory() {
    }

    /** Known failure (400 / 404 / 422 / 502 / 503). */
    public static ApiResponse from(ApiException e) {

        return new ApiResponse(e.getStatus(), generateErrorDto(e));

    }

    public static ApiResponse fromUnexpected(Exception e) {
        var errorDto = new ErrorDto("INTERNAL_SERVER_ERROR", "Internal Server Error", null);
        return new ApiResponse(500, errorDto);

    }

    static ErrorDto generateErrorDto(ApiException apiException) {

        var errorDto = new ErrorDto(apiException.getCode(), apiException.getMessage(), null);

        if (apiException instanceof ValidationException valEx) {
            errorDto = new ErrorDto(valEx.getCode(), valEx.getMessage(), valEx.getIssues());
        } else if (apiException instanceof BadGatewayException badGatewayException) {
            errorDto = new ErrorDto(badGatewayException.getCode(), badGatewayException.getMessage(),
                    badGatewayException.getIssues());
        }

        return errorDto;

    }
};
