package it.unipi.lsmsd.exception;

public class RegionCodeNotRecognizedException extends BucketException {
    public RegionCodeNotRecognizedException(String message) {
        super(message);
    }
    public RegionCodeNotRecognizedException(String message, Throwable cause) {
    super(message, cause);
  }
}
