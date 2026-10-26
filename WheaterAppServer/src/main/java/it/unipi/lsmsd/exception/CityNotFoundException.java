package it.unipi.lsmsd.exception;

public class CityNotFoundException extends CityException{
    public CityNotFoundException(String message) {
        super(message);
    }
    public CityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
