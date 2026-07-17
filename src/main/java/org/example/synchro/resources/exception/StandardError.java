package org.example.synchro.resources.exception;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record StandardError(
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd' 'HH:mm:ss", timezone = "GMT")
    Instant moment,
    int value,
    String error,
    String message,
    String requestURI
){}