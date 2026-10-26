package it.unipi.lsmsd.exception;

public class BucketException extends RuntimeException {
    public BucketException(String message) {
        super(message);
    }
    public BucketException(String message, Throwable cause) {
        super(message, cause);
    }
}
