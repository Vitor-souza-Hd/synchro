package org.example.synchro.resources.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.example.synchro.services.exception.BadCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;

@ControllerAdvice
public class ResourceExceptionHandler {
	
	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<StandardError> BadCredential(BadCredentialsException e, HttpServletRequest request){
		String error = "email ou senha inválidos";
		HttpStatus status = HttpStatus.NOT_FOUND;
		StandardError err = new StandardError(Instant.now(),status.value(),error,e.getMessage(),request.getRequestURI());
		return ResponseEntity.status(status).body(err);
	}
	
}
