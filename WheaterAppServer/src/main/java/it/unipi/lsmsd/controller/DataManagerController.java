package it.unipi.lsmsd.controller;

import java.io.IOException;

import it.unipi.lsmsd.exception.UnauthorizedException;
import it.unipi.lsmsd.model.Role;
import it.unipi.lsmsd.service.AutomatingService;
import it.unipi.lsmsd.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;

import it.unipi.lsmsd.service.DataInitializeService;
import it.unipi.lsmsd.service.DataRefreshService;

@RestController
@RequestMapping("/data-manager")
public class DataManagerController {

    @Autowired
    DataInitializeService dataInitializeService;

    @Autowired
    DataRefreshService dataRefreshService;

    @Autowired
    private UserService userService;

    @Autowired
    private AutomatingService automatingService;


    @PutMapping("/update/forecasts")
    public ResponseEntity<Object> updateForecasts(@RequestHeader("Authorization") String token) {
        try{
            automatingService.updateForecastsAsync(token);
            return ResponseEntity
                    .status(HttpStatus.OK).build();
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch(Exception ex){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + ex.getMessage());
        }
    }
    @PutMapping("/update/measurements")
    public ResponseEntity<Object> updateMeasurements(@RequestHeader("Authorization") String token) {
        try{
            automatingService.updateMeasurementsAsync(token);
            return ResponseEntity
                    .status(HttpStatus.OK).build();
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch(Exception ex){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + ex.getMessage());
        }
    }
    @PutMapping("/update/ewes")
    public ResponseEntity<Object> updateEWEs(@RequestHeader("Authorization") String token) {
        try{
            automatingService.updateExtremeWeatherEventsAsync(token);
            return ResponseEntity
                    .status(HttpStatus.OK).build();
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch(Exception ex){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + ex.getMessage());
        }
    }

    @PostMapping("initialize-all")
    public ResponseEntity<String> initializeAll(
            @RequestHeader("Authorization") String token) throws IOException{
        try {
            userService.getAndCheckUserFromToken(token, Role.ADMIN);
// Step 1
            dataInitializeService.initializeCitiesMongo();
            System.out.println();
            System.out.println("===========================================");
            System.out.println("== [STEP 1A COMPLETED] initializeCitiesMongo");
            System.out.println("===========================================");
            System.out.println();

            dataInitializeService.initializeCitiesRedis();
            System.out.println();
            System.out.println("===========================================");
            System.out.println("== [STEP 1B COMPLETED] initializeCitiesRedis");
            System.out.println("===========================================");
            System.out.println();

// Step 2
            dataInitializeService.initializeMeasurements();
            System.out.println();
            System.out.println("===========================================");
            System.out.println("== [STEP 2 COMPLETED] initializeMeasurements");
            System.out.println("===========================================");
            System.out.println();

// Step 3
            dataRefreshService.refreshHistoricalMeasurement();
            System.out.println();
            System.out.println("===========================================");
            System.out.println("== [STEP 3 COMPLETED] refreshHistoricalMeasurement");
            System.out.println("===========================================");
            System.out.println();

// Step 4
            dataRefreshService.initializeExtremeWeatherEvents();
            System.out.println();
            System.out.println("===========================================");
            System.out.println("== [STEP 4 COMPLETED] initializeExtremeWeatherEvents");
            System.out.println("===========================================");
            System.out.println();

// Step 5
            dataRefreshService.refreshForecast();
            System.out.println();
            System.out.println("===========================================");
            System.out.println("== [STEP 5 COMPLETED] refreshForecast");
            System.out.println("===========================================");
            System.out.println();

        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch (Exception e){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.OK).body("Initialization complete. Check Log to Verify");
    }

    @PostMapping("initialize-1A-cities-mongo")
    public ResponseEntity<String> initializeCityDataMongo(
            @RequestHeader("Authorization") String token) throws IOException{
        try {
            userService.getAndCheckUserFromToken(token, Role.ADMIN);
            dataInitializeService.initializeCitiesMongo();
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch (Exception e){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.OK).body("Citites Data Initialized successfully. Check Log to Verify");
    }

    @PostMapping("initialize-1B-cities-redis")
    public ResponseEntity<String> initializeCityDataRedis(
            @RequestHeader("Authorization") String token) throws IOException{
        try {
            userService.getAndCheckUserFromToken(token, Role.ADMIN);
            dataInitializeService.initializeCitiesRedis();
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch (Exception e){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.OK).body("Citites Data Initialized successfully. Check Log to Verify");
    }

    @PostMapping("initialize-2-hourly-measurements")
    public ResponseEntity<String> initializeMeasurementData(
            @RequestHeader("Authorization") String token) throws IOException{
        try{
            userService.getAndCheckUserFromToken(token, Role.ADMIN);
            dataInitializeService.initializeMeasurements();
            return ResponseEntity.status(HttpStatus.OK).body("Measurements Data Initialized successfully. Check Log to Verify");
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch (Exception e){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }
    }

    @PostMapping("initialize-3-refresh-historical")
    public ResponseEntity<String> refreshHistoricalMeasurement(
            @RequestHeader("Authorization") String token) throws JsonProcessingException, IOException{
        try{
            userService.getAndCheckUserFromToken(token, Role.ADMIN);
            dataRefreshService.refreshHistoricalMeasurement();
            return ResponseEntity.status(HttpStatus.OK).body("Historical Measurements Data Refreshed successfully. Check Log to Verify");
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch (Exception e){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }
    }

    @PostMapping("initialize-4-extremeWeatherEvents")
    public ResponseEntity<String> initializeExtremeWeatherEvents(
            @RequestHeader("Authorization") String token){
        try{
            userService.getAndCheckUserFromToken(token, Role.ADMIN);
            dataRefreshService.initializeExtremeWeatherEvents();
            return ResponseEntity.status(HttpStatus.OK).body("EWE initialized successfully. Check Log to Verify");
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch (Exception e){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }
    }

    @PostMapping("initialize-5-refresh-forecast")
    public ResponseEntity<String> refreshforecast(
            @RequestHeader("Authorization") String token) throws JsonProcessingException{
        try{
        userService.getAndCheckUserFromToken(token, Role.ADMIN);
        dataRefreshService.refreshForecast();
        return ResponseEntity.status(HttpStatus.OK).body("Forecast Data Refreshed successfully. Check Log to Verify");
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch (Exception e){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error: " + e.getMessage());
        }
    }
}
