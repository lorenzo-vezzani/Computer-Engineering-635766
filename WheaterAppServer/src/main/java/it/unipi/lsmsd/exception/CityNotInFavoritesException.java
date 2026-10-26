package it.unipi.lsmsd.exception;

public class CityNotInFavoritesException extends RuntimeException {
    public CityNotInFavoritesException(String message) { super(message); }
    public CityNotInFavoritesException(String message, Throwable cause) { super(message, cause); }
}
