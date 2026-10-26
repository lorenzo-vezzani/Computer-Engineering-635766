package it.unipi.lsmsd.controller;

import it.unipi.lsmsd.model.ExtremeWeatherEventCategory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleEnumConversionError(MethodArgumentTypeMismatchException ex) {


        if (ex.getRequiredType() == ExtremeWeatherEventCategory.class) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Invalid value for ExtremeWeatherEventCategory. Accepted values are: " +
                            Arrays.toString(ExtremeWeatherEventCategory.values()));
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Error in String to Enum conversion, ensure that you put a valid string");
    }
}
