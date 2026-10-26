package it.unipi.lsmsd.exception;

public class BucketNotDefinedForRegionException extends BucketException {
    public BucketNotDefinedForRegionException(String message) {
        super(message);
    }
    public BucketNotDefinedForRegionException(String message, Throwable cause) {
        super(message, cause);
    }
}
