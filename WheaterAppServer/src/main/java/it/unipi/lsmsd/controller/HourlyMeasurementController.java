package it.unipi.lsmsd.controller;

import java.io.IOException;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import it.unipi.lsmsd.service.RedisForecastService;

@RestController
@RequestMapping("/forecast")
public class HourlyMeasurementController {

    @Autowired
    private RedisForecastService forecastRedisService;


    /**
     * Handles HTTP GET requests to retrieve the 24-hour weather forecast for a specified city.
     *
     * @param cityId the unique identifier of the city for which the forecast is requested
     * @return a {@link ResponseEntity} containing the 24-hour forecast as a JSON-formatted string and an HTTP status code 200 (OK)
     */
    @GetMapping("/today")
    public ResponseEntity<Object> get24HrForecast(@RequestParam String cityId) {
        try {
            LocalDate today = LocalDate.now();
            String jsonForecast = forecastRedisService.getForecastTargetDay(cityId, today);
            return ResponseEntity.status(HttpStatus.OK).body(jsonForecast);
        }
        catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Handles HTTP GET requests to retrieve the 24-hour weather forecast for a specified city and date.
     *
     * @param cityId the unique identifier of the city for which the forecast is requested
     * @param targetDate the {@link LocalDate} representing the day (in UTC) for which the forecast is desired
     * @return a {@link ResponseEntity} containing the forecast as a JSON-formatted string and an HTTP status code 200 (OK)
     */
    @GetMapping("/day")
    public ResponseEntity<Object> get24HrForecast(@RequestParam String cityId, @RequestParam LocalDate targetDate) {
        try {
            String jsonForecast = forecastRedisService.getForecastTargetDay(cityId, targetDate);
            return ResponseEntity.status(HttpStatus.OK).body(jsonForecast);
        }
        catch(IllegalStateException ISe){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Illegal argument: targetDate specified is not available");
        }
        catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Handles HTTP GET requests to retrieve the 7-day weather forecast for a specified city.
     *
     * @param cityId the unique identifier of the city for which the forecast is requested
     * @return a {@link ResponseEntity} containing the 7-day forecast as a JSON-formatted string and an HTTP status code 200 (OK)
     * @throws IOException if an I/O error occurs during the retrieval of the forecast data
     */
    @GetMapping("/week")
    public ResponseEntity<Object> get7DayForecast(@RequestParam String cityId) throws IOException {
        try {
            String jsonForecast = forecastRedisService.get7DayForecast(cityId);
            return ResponseEntity.status(HttpStatus.OK).body(jsonForecast);
        }
        catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Handles HTTP GET requests to retrieve the 24-hour weather forecast for an arbitrary city
     * identified by region, latitude, and longitude.
     * <p>
     * The forecast corresponds to the current UTC day and is retrieved based on geolocation parameters.
     *
     * @param region the name of the region in which the city is located
     * @param latitude the geographic latitude of the city
     * @param longitude the geographic longitude of the city
     * @return a {@link ResponseEntity} containing the forecast as a JSON-formatted string and an HTTP status code 200 (OK)
     */
    @GetMapping("/today/arbitrary-city")
    public ResponseEntity<Object> get24HrForecastArbCity(
            @RequestParam String region,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam Double elevation
    ) {
        try {
            LocalDate today = LocalDate.now();

            String jsonForecast = forecastRedisService.getForecastArbitraryCityTargetDay(region, latitude, longitude, elevation, today);
            return ResponseEntity.status(HttpStatus.OK).body(jsonForecast);
        }
        catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }
    }

    @GetMapping("/day/arbitrary-city")
    public ResponseEntity<Object> get24HrForecastArbCity(
            @RequestParam String region,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam Double elevation,
            @RequestParam LocalDate targetDate
    ) {
        try {
            String jsonForecast = forecastRedisService.getForecastArbitraryCityTargetDay(region, latitude, longitude, elevation, targetDate);
            return ResponseEntity.status(HttpStatus.OK).body(jsonForecast);
        }
        catch(IllegalStateException ISe){
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Illegal argument: targetDate specified is not available");
        }
        catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }
    }

}
