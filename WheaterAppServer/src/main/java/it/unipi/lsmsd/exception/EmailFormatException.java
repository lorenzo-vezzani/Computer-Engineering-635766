package it.unipi.lsmsd.exception;

public class EmailFormatException extends RuntimeException {
    public EmailFormatException(String message) {
        super(message);
    }
    public EmailFormatException(String message, Throwable cause) { super(message, cause); }
}
