package org.example.synchro.services.exception;

public class BadCredentialsException extends RuntimeException{
    public BadCredentialsException(){
        super("email ou senha inválidos");
    }
}
