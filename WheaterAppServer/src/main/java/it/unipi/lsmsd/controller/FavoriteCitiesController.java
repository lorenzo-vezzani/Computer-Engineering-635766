package it.unipi.lsmsd.controller;

import it.unipi.lsmsd.exception.UnauthorizedException;
import it.unipi.lsmsd.service.FavoriteCityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/favorites")
public class FavoriteCitiesController {

    @Autowired
    private FavoriteCityService favoriteCityService;

    @GetMapping
    public ResponseEntity<List<String>> getFavorites(@RequestHeader("Authorization") String token) {
        try {
            List<String> response = favoriteCityService.getFavorites(token);
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(response);
        } catch (UnauthorizedException Ue) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonList("Unauthorized: " + Ue.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList("An unexpected error occurred: " + ex.getMessage()));
        }
    }

    @PutMapping()
    public ResponseEntity<String> addToFavorites(@RequestHeader("Authorization") String token, @RequestParam String targetCityId){
        try{
            String response = favoriteCityService.addToFavorites(token, targetCityId);
            // success
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(response);

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


    @DeleteMapping()
    public ResponseEntity<String> removeFromFavorites(@RequestHeader("Authorization") String token, @RequestParam String targetCityId){
        try{
            String response = favoriteCityService.removeFromFavorites(token, targetCityId);
            // success
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(response);

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
}
