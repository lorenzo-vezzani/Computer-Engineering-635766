package it.unipi.lsmsd.controller;

import it.unipi.lsmsd.DTO.CityDTO;
import it.unipi.lsmsd.exception.CityException;
import it.unipi.lsmsd.exception.CityNotFoundException;
import it.unipi.lsmsd.exception.UnauthorizedException;
import it.unipi.lsmsd.model.CityBasicProjection;
import it.unipi.lsmsd.model.Role;
import it.unipi.lsmsd.repository.CityRepository;
import it.unipi.lsmsd.service.CityService;
import it.unipi.lsmsd.service.HourlyMeasurementService;
import it.unipi.lsmsd.service.UserService;
import it.unipi.lsmsd.utility.Mapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/city")
public class CityController {

    @Autowired
    private CityService cityService;
    @Autowired
    private UserService userService;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private HourlyMeasurementService hourlyMeasurementService;

    @PostMapping("/add")
    public ResponseEntity<String> addCity(@RequestHeader("Authorization") String token, @RequestBody CityDTO cityDTO) {
        try{
            userService.getAndCheckUserFromToken(token, Role.ADMIN);
            String cityId = cityService.saveCity(cityDTO, token);
            hourlyMeasurementService.refreshHourlyMeasurementsFromOpenMeteoAsync(cityId, cityDTO.getPastDaysMeasurementsUpdate());

            return ResponseEntity
                    .status(HttpStatus.CREATED)  // 201 for creation
                    .body("City added successfully");
        }
        catch(DuplicateKeyException ex){
            return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body("City already exits: " + ex.getMessage());
        }
        catch(IllegalArgumentException IAe){
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("Illegal argument: " + IAe.getMessage());
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @PutMapping("/thresholds")
    public ResponseEntity<Object> updateCityThresholds(@RequestHeader("Authorization") String token, @RequestBody CityDTO cityDTO) {
        try{
            userService.getAndCheckUserFromToken(token, Role.ADMIN);
            // Update the city threshold
            cityService.updateCityThresholds(cityDTO, token);

            String cityId = Mapper.mapCity(cityDTO).getId();
            CityDTO cityDtoAfter = cityService.getCityWithID(cityId);

            // Returns all city's information into the body
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(cityDtoAfter);
        }
        catch (CityNotFoundException CNFe) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("City not found: " + CNFe.getMessage());
        }
        catch (CityException Ce) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("City not identifiable: " + Ce.getMessage());
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @GetMapping("/by-name")
    public ResponseEntity<Object> getCityByName(@RequestParam String cityName){
        try{
            // Retrieves the city
            List<CityBasicProjection> cities = cityRepository.findAllByNameOrderByFollowers(cityName);

            // Returns all city's information into the body
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(cities);
        }
        catch(NoSuchElementException NSEe){
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("City not found: " + NSEe.getMessage());
        }
        catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }
    @GetMapping("/all")
    public ResponseEntity<Object> getAllCities(){
        try{
            List<CityBasicProjection> cities = cityRepository.findAllBy();

            // Returns all city's information into the body
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(cities);
        }
        catch(NoSuchElementException NSEe){
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("City not found: " + NSEe.getMessage());
        }
        catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }
}
