package it.unipi.lsmsd.exception;

public class CityAlreadyInFavoritesException extends RuntimeException {
    public CityAlreadyInFavoritesException(String message) {
        super(message);
    }
    public CityAlreadyInFavoritesException(String message, Throwable cause) { super(message, cause); }
}
