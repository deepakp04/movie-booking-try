package com.moviebooking.common.response;

import java.util.List;

public class ErrorResponse {

    private boolean success;

    private String message;

    private List<String> errors;

    public ErrorResponse(boolean success, String message, List<String> errors) {
        this.success = success;
        this.message = message;
        this.errors = errors;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getErrors() {
        return errors;
    }
}