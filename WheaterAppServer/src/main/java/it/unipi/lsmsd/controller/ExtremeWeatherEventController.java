package it.unipi.lsmsd.controller;

import it.unipi.lsmsd.exception.CityNotFoundException;
import it.unipi.lsmsd.exception.ThresholdsNotPresentException;
import it.unipi.lsmsd.exception.UnauthorizedException;
import it.unipi.lsmsd.model.ExtremeWeatherEvent;
import it.unipi.lsmsd.model.Role;
import it.unipi.lsmsd.service.ExtremeWeatherEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import it.unipi.lsmsd.service.UserService;

@RestController
@RequestMapping("/ewe")
public class ExtremeWeatherEventController {

    @Autowired
    private ExtremeWeatherEventService extremeWeatherEventService;

    @Autowired
    private UserService userService;

    /**
     * Deletes overlapping or duplicate extreme weather events (EWEs) for a specific city
     * within a given time interval. This operation is restricted to users with administrative privileges.
     * <p>
     * The method authenticates the user via the provided authorization token, fetches
     * all EWEs for each category occurring in the specified time range, and merges any
     * overlapping entries based on their timestamps and intensity. The result includes
     * a count of removed and inserted EWEs.
     *
     * @param token      the authorization token of the user performing the request; must belong to an admin
     * @param cityId     the identifier of the city whose EWEs are to be cleaned
     * @param startTime  the start of the time interval for which EWEs should be processed
     * @param endTime    the end of the time interval for which EWEs should be processed
     * @return           a JSON-formatted {@link ResponseEntity} containing a map with keys
     *                   {@code removed} and {@code inserted} indicating how many events were removed and added
     */
    @DeleteMapping("/duplicates/range")
    public ResponseEntity<Object> cleanUpExtremeWeatherEventRange(
            @RequestHeader("Authorization") String token,
            @RequestParam String cityId,
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime
    ) {
        Map<String, Integer> cleanupResult;
        try {
            userService.getAndCheckUserFromToken(token, Role.ADMIN);
            // Call the relative service
            cleanupResult = extremeWeatherEventService.cleanExtremeWeatherEventDuplicatesRange(cityId, startTime, endTime);
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch(Exception e){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error: " + e.getMessage());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(cleanupResult);
    }

    /**
     * Deletes all overlapping or duplicate extreme weather events (EWEs) for a specific city,
     * regardless of their timestamp. This operation is restricted to users with administrative privileges.
     * <p>
     * The method authenticates the user via the provided authorization token, retrieves all
     * EWEs for each category associated with the given city, and merges any overlapping entries
     * based on their start and end times as well as their intensity. The outcome is a summary
     * of how many events were removed and how many merged entries were inserted.
     *
     * @param token   the authorization token of the user performing the request; must belong to an admin
     * @param cityId  the identifier of the city whose EWEs are to be cleaned
     * @return        a JSON-formatted {@link ResponseEntity} containing a map with keys
     *                {@code removed} and {@code inserted} indicating the number of events deleted and created
     */
    @DeleteMapping("/duplicates/all")
    public ResponseEntity<Object> cleanUpExtremeWeatherEventRange(
            @RequestHeader("Authorization") String token,
            @RequestParam String cityId
    ) {
        try{
            userService.getAndCheckUserFromToken(token, Role.ADMIN);
            // Call the relative service
            Map<String, Integer> cleanupResult = extremeWeatherEventService.cleanExtremeWeatherEventDuplicatesAll(cityId);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cleanupResult);
        }
        catch(UnauthorizedException Ue){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + Ue.getMessage());
        }
        catch (Exception e){
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server error: " + e.getMessage());
        }
    }
}
