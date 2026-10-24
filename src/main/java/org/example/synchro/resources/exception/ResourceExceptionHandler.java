package org.example.synchro.resources.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.example.synchro.services.exception.BadCredentialsException;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceConflictException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.time.LocalDate;

@ControllerAdvice
public class ResourceExceptionHandler {
	
	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<StandardError> BadCredential(BadCredentialsException e, HttpServletRequest request){
		String error = "bad request";
		HttpStatus status = HttpStatus.BAD_REQUEST;
		StandardError err = new StandardError(Instant.now(),status.value(),error,e.getMessage(),request.getRequestURI());
		return ResponseEntity.status(status).body(err);
	}

	@ExceptionHandler(ResourceConflictException.class)
	public ResponseEntity<StandardError> ResourceConflict(ResourceConflictException e, HttpServletRequest request){
		return ResponseEntity.status(HttpStatus.CONFLICT).body(
				new StandardError(
						Instant.now(),
						409,
						"Resource conflict.",
						e.getMessage(),
						request.getRequestURI()
				)
		);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<StandardError> ResourceNotFound(ResourceNotFoundException e, HttpServletRequest request){
		HttpStatus status = HttpStatus.NOT_FOUND;
		StandardError err = new StandardError(
				Instant.now(),
				status.value(),
				"resource not found",
				e.getMessage(),
				request.getRequestURI()
		);
		return ResponseEntity.status(status).body(err);
	}

	@ExceptionHandler(InvalidTokenException.class)
	public ResponseEntity<StandardError> InvalidToken(InvalidTokenException e, HttpServletRequest request){
		HttpStatus status = HttpStatus.UNAUTHORIZED;
		String error = "invalid token";
		StandardError err = new StandardError(Instant.now(),status.value(),error,e.getMessage(), request.getRequestURI());
		return ResponseEntity.status(status).body(err);
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<StandardError> Unauthorized(UnauthorizedException e, HttpServletRequest request){
		HttpStatus status = HttpStatus.UNAUTHORIZED;
		StandardError err = new StandardError(
				Instant.now(),
				status.value(),
				"unauthorized",
				e.getMessage(),
				request.getRequestURI()
		);
		return ResponseEntity.status(status).body(err);
	}
	
}
