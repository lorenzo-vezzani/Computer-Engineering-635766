package it.unipi.lsmsd.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;

import com.fasterxml.jackson.core.JsonProcessingException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@ControllerAdvice
public class GlobalControllerExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalControllerExceptionHandler.class);

    /* BAD REQUESTS */
    // Handle JsonProcessingException
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<String> handleJsonProcessingException(JsonProcessingException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                             .body("Malformed or unprocessable JSON: " + ex.getMessage());
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<String> handleNullPointerException(NullPointerException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("A required field is missing: " + ex.getMessage());
    }

    // Handle JedisDataException (redis data error)
    @ExceptionHandler(JedisDataException.class)
    public ResponseEntity<String> handleJedisDataException(JedisDataException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                             .body("Redis data error: " + ex.getMessage());
    }

    // Handle IllegalArgumentException (invalid argument provided)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                             .body("Invalid data: " + ex.getMessage());
    }

    /* SERVICE UNAVAILABLE - MongoDB, Redis and Open-Meteo*/
   
    // Handle JedisConnectionException (redis is unavailable)
    @ExceptionHandler(JedisConnectionException.class)
    public ResponseEntity<String> handleJedisConnectionException(JedisConnectionException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body("Redis is unavailable: " + ex.getMessage());
    }

    // Handle HttpServerErrorException (service down, 5xx)
    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<String> handleHttpServerErrorException(HttpServerErrorException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                             .body("Server error: " + ex.getMessage());
    }

    /* INTERNAL SERVER ERROR - Error in Weather App */

    // Handle HttpClientErrorException (client-side error, 4xx)
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<String> handleHttpClientErrorException(HttpClientErrorException ex) {
        logger.error("Internal Server Error: ", ex);
        return ResponseEntity.status(ex.getStatusCode())
                             .body("Client error: " + ex.getMessage());
    }

    // Handle general DataAccessException (database errors)
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> handleDataAccessException(DataAccessException ex) {
        logger.error("Internal Server Error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body("Database access error: " + ex.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException ex) {
        logger.error("Internal Server Error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("I/O error occurred: " + ex.getMessage());
    }

    // Handle generic Exception (catch all)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        logger.error("Internal Server Error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body("An unexpected error occurred: " + ex.getMessage());
    }

    /* Custom Handlers  */
    @ExceptionHandler(CityException.class)
    public ResponseEntity<String> handleCityException(Exception ex) {
        logger.error("Internal Server Error: ", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                             .body(ex.getMessage());

    }    


}
