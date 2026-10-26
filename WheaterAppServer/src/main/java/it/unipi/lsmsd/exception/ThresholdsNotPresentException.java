package it.unipi.lsmsd.exception;

public class ThresholdsNotPresentException extends CityException {

    public ThresholdsNotPresentException(String message) { super(message); }

    public ThresholdsNotPresentException(String message, Throwable cause) { super(message, cause); }
}
