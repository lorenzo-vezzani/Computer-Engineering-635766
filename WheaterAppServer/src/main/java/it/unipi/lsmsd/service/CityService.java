package it.unipi.lsmsd.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unipi.lsmsd.DTO.APIResponseDTO;
import it.unipi.lsmsd.DTO.CityDTO;
import it.unipi.lsmsd.DTO.HourlyMeasurementDTO;
import it.unipi.lsmsd.exception.CityException;
import it.unipi.lsmsd.exception.CityNotFoundException;
import it.unipi.lsmsd.model.City;
import it.unipi.lsmsd.model.EWEThreshold;
import it.unipi.lsmsd.model.Role;
import it.unipi.lsmsd.repository.CityRepository;
import it.unipi.lsmsd.utility.ISODateUtil;
import it.unipi.lsmsd.utility.Mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.dao.DuplicateKeyException;
import redis.clients.jedis.JedisCluster;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CityService {

    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private DataHarvestService dataHarvestService;
    @Autowired
    private JedisCluster jedisCluster;
    @Autowired
    private UserService userService;
    @Autowired
    private RedisForecastService forecastRedisService;

    // Get City info with City Name
    public List<CityDTO> getCity(String cityName) throws NoSuchElementException {
        List<City> cities = cityRepository.findAllByName(cityName);
        // throws NoSuchElementException if no city is found
        if (cities.isEmpty()) { throw new NoSuchElementException("City not found with name: " + cityName ); }
        // Map the list of city to list of cityDTO
        List<CityDTO> cityDTOs = cities.stream().map(city -> Mapper.mapCityDTO(city))
                               .collect(Collectors.toList());
        return cityDTOs;
    }

    // Get City info with City Id
    public CityDTO getCityWithID(String cityID) throws NoSuchElementException {
        Optional<City> city = cityRepository.findById(cityID);
        // throws NoSuchElementException is no city is found
        if (!city.isPresent()) { throw new NoSuchElementException("City not found with id: " + cityID); }
        return  Mapper.mapCityDTO(city.get());
    }

    // Saves the city to the DB and returns the cityID
    // Alert!!! : Throws DuplicateKeyException -> Need to handle it by the class that calls this method
    /*
    * needing of intra-db consistency if mongo and/or redis write fails everything is rollbacked
    * */
    public String saveCity(CityDTO cityDTO, String token) throws DuplicateKeyException, JsonProcessingException {
        userService.getAndCheckUserFromToken(token, Role.ADMIN);
        City city = Mapper.mapCityWithThresholds(cityDTO);
        String redisKey = "city:{" + city.getId().substring(0,3) + "}" + city.getId().substring(3);
        boolean insertedMongo = false;
        boolean insertedRedis = false;

        try {
            // 1. adding in MongoDB
            cityRepository.insert(city);
            insertedMongo = true;

            // 2. adding in Redis
            Map<String, String> c = new HashMap<>();
            c.put("name", city.getName());
            c.put("region", city.getRegion());
            c.put("elevation", city.getElevation().toString());
            jedisCluster.hset(redisKey, c);
            insertedRedis = true;

            // week forecast adding
            CityDTO fullCityDTO = getCityWithID(city.getId());
            APIResponseDTO apiResponseDTO = dataHarvestService.getCityForecast(
                    fullCityDTO.getLatitude(), fullCityDTO.getLongitude(), 0, 7
            );
            HourlyMeasurementDTO hourlyMeasurementDTO = apiResponseDTO.getHourly();
            hourlyMeasurementDTO.setCityId(city.getId());
            forecastRedisService.saveForecast(hourlyMeasurementDTO);

            return city.getId();
        } catch (Exception e) {
            // ROLLBACK if necesssary
            if (insertedRedis) {
                jedisCluster.del(redisKey);
            }
            if (insertedMongo) {
                cityRepository.deleteById(city.getId());
            }
            throw e;
        }
    }
    
    // Updates the city with the lastest date of historical data
    public void updateStartEndDate( String startDate, String endDate, String cityId) {
        Optional<City> cityOpt = cityRepository.findById(cityId);
        if (cityOpt.isPresent()) {
            City city = cityOpt.get();
            
            //"2025-03-15T00:00" -> 2025-03-15T00:00:00.000+00:00
            city.setStartDate(ISODateUtil.getISODate(startDate)); 
            city.setEndDate(ISODateUtil.getISODate(endDate)); 

            cityRepository.save(city);
        } else {
            throw new RuntimeException("City not found with id: " + cityId);
        }
    }

    public String saveCities(List<CityDTO> cityDTOs){
        List<City> cities = new ArrayList<>();
        for(CityDTO cityDTO: cityDTOs){ cities.add(Mapper.mapCity(cityDTO));}
        cityRepository.saveAll(cities);
        return "Saved";
    }

    /**
     * Updates the city thresholds
     * @param cityId City id
     * @param eweThreshold the thresholds
     * @throws CityNotFoundException on city not found
     * @throws CityException on city not identifiable
     */
    public void updateCityThresholds(String cityId, EWEThreshold eweThreshold) throws CityException {
        try {
            Optional<City> cityOpt = cityRepository.findById(cityId);

            if (cityOpt.isEmpty()) {
                throw new CityNotFoundException("City with ID " + cityId + " not found.");
            }

            City city = cityOpt.get();
            city.setEweThresholds(eweThreshold);
            cityRepository.save(city);

        } catch (IllegalArgumentException e) {
            throw new CityException("City ID is invalid: " + cityId, e);
        } catch (CityNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new CityException("Unexpected error while updating city thresholds", e);
        }
    }


    /**
     * Updates the city thresholds
     * @param cityDTO CityDTO with the new thresholds included
     * @throws CityNotFoundException on city not found
     * @throws CityException on city not identifiable
     */
    public void updateCityThresholds(CityDTO cityDTO, String token) throws CityException {
        // Check if the user's role is ADMIN
        userService.getAndCheckUserFromToken(token, Role.ADMIN);

        // Check if the DTO has the necessary fields for city identification
        if(!cityDTO.hasIdFields()){
            throw new CityException("City id fields not provided: name, region, longitude, latitude");
        }

        // Maps to the city model
        City city = Mapper.mapCityWithThresholds(cityDTO);

        // Gets the city
        Optional<City> cityOpt = cityRepository.findById(city.getId());

        if(cityOpt.isEmpty()){
            throw new CityNotFoundException("City not found, add the city before updating");
        }

        // If the city is already present it updates the EweThresholds field
        City existingCity = cityOpt.get();
        existingCity.setEweThresholds(cityDTO.getEweThresholds());
        cityRepository.save(existingCity);
    }

    public LocalDateTime getLastEweUpdateById(String cityId) throws IllegalArgumentException{
        Optional<City> city = cityRepository.findById(cityId);

        if(city.isEmpty()){
            throw new CityNotFoundException("City " + cityId + " not found");
        }

        return city.get().getLastEweUpdate();
    }

    public void setLastEweUpdateById(String cityId, LocalDateTime newLastEweUpdate) throws IllegalArgumentException{
        Optional<City> city = cityRepository.findById(cityId);

        if(city.isEmpty()){
            throw new IllegalArgumentException("City not found");
        }

        City existingCity = city.get();
        existingCity.setLastEweUpdate(newLastEweUpdate);

        // Save the updated city back to the database
        cityRepository.save(existingCity);
    }

    public void setLastMeasurementUpdateById(String cityId, LocalDateTime newLastMeasurementUpdate) throws IllegalArgumentException{
        Optional<City> city = cityRepository.findById(cityId);

        if(city.isEmpty()){
            throw new IllegalArgumentException("City not found");
        }

        City existingCity = city.get();
        existingCity.setLastMeasurementUpdate(newLastMeasurementUpdate);

        // Save the updated city back to the database
        cityRepository.save(existingCity);
    }
}
