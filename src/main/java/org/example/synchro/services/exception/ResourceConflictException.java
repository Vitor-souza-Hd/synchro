package org.example.synchro.services.exception;

public class ResourceConflictException extends RuntimeException {
    public ResourceConflictException(String value , String product) {
        super("conflito de recursos: " + value + ": " + product + " já está em uso");
    }
}
