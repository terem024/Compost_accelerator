package com.group11.compostsystem.service;

public class NoActiveBatchException extends IllegalStateException {

    public NoActiveBatchException(String message) {
        super(message);
    }
}
