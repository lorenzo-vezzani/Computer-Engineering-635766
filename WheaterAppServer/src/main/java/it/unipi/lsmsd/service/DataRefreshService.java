package it.unipi.lsmsd.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import it.unipi.lsmsd.exception.CityNotFoundException;
import it.unipi.lsmsd.exception.ThresholdsNotPresentException;
import it.unipi.lsmsd.model.City;
import it.unipi.lsmsd.repository.CityRepository;
import it.unipi.lsmsd.utility.MongoInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DuplicateKeyException;

import it.unipi.lsmsd.DTO.APIResponseDTO;
import it.unipi.lsmsd.DTO.CityDTO;
import it.unipi.lsmsd.DTO.HourlyMeasurementDTO;
import redis.clients.jedis.JedisCluster;

@Service
public class DataRefreshService {
    @Autowired
    private HourlyMeasurementService hourlyMeasurementService;
    @Autowired
    private DataHarvestService dataHarvestService;
    @Autowired
    private CityService cityService;
    @Autowired
    private RedisForecastService forecastRedisService;
    @Autowired
    private ExtremeWeatherEventService extremeWeatherEventService;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private JedisCluster jedisCluster;

    private final ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(MongoInitializer.class);

    public DataRefreshService(){
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /*
     * Add
     *  Get the lis
     *  For each city get the city.endDate and compare with current date
     * Get Historical Data with the date range
     * Delete the
     */
    public void refreshHistoricalMeasurement() throws JsonProcessingException, IOException{
        // Use PathMatchingResourcePatternResolver to load the resource from the correct path
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:data_init/citiesId.json");

        // Get available processors to determine thread pool size
        int threadCount = Math.min(4, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        try (InputStream is = resource.getInputStream()){
            List<String> cityIdList = objectMapper.readValue(is, new TypeReference<List<String>>() {});

            // Submit tasks for parallel processing
            for(String cityId : cityIdList){
                // refreshCityHistoricalData(cityId);
                Callable<Void> task = () -> {
                    refreshCityHistoricalData(cityId);
                    return null;
                };
                futures.add(executor.submit(task));
            }

            // Wait for all tasks to complete
            for (Future<?> future : futures) {
                try {
                    future.get(); // Blocking call, waits for task to finish
                } catch (Exception e) {
                    e.printStackTrace(); // Handle any task exception
                }
            }
        }
        catch (Exception ignored){
        } finally {
            executor.shutdown(); // Ensure that the executor shuts down after all tasks are complete
        }
    }

    private void refreshCityHistoricalData(String cityId) throws Exception{

        try {
            CityDTO cityDTO = cityService.getCityWithID(cityId);
            // Check for the Start and End Date
            if(cityDTO == null || cityDTO.getStartDate()==null || cityDTO.getEndDate()== null){ return;}

            DateTimeFormatter zonedFormatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
            DateTimeFormatter isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            ZoneId zoneId = ZoneId.of("UTC");

            // STEP 1: Save the Fresh Historical Data
            // Convert both dates to Local Timezone of the cities
            // Get the City endDate, parse to ZonedDateTime with Local timezone, add one day and fromat to (yyyy-MM-dd)
            // Add one hour
            // cityDTO.getEndDate() "Thu May 01 23:00:00 UTC 2025" --> hourly_startDate "2025-05-02"
            String hourly_startDate = ZonedDateTime.parse(cityDTO.getEndDate(), zonedFormatter)
                .plusHours(1)
                .format(isoFormatter);
            // The endDate would be yesterday so subtract a day
            // "2025-05-07"
            String hourly_endDate = LocalDate.now(zoneId)
                .minusDays(1)
                .toString();

            // Check if start date is greater than end date which means Data already updated
            if (LocalDate.parse(hourly_startDate).isAfter(LocalDate.parse(hourly_endDate))) {
                return;
            }

            // Get data from Open-Meteo API
            // System.out.print("Refreshing: "+ cityId+" FROM:"+hourly_startDate+"TO"+hourly_endDate+" START");
            APIResponseDTO apiResponseDTO = dataHarvestService.getCityHistoricalMeasurement(
                cityDTO.getLatitude(),cityDTO.getLongitude(), hourly_startDate, hourly_endDate);
            HourlyMeasurementDTO hourlyMeasurementDTO = apiResponseDTO.getHourly();
            hourlyMeasurementDTO.setCityId(cityId);
            // System.out.print("Refreshing: "+ cityId+" FROM:"+hourly_startDate+"TO"+hourly_endDate+" DONE, saving to MongoDB");
            //Save to MongoDB
            hourlyMeasurementService.saveHourlyMeasurements(apiResponseDTO.getHourly());
            // System.out.print("Refreshing: "+ cityId+" FROM:"+hourly_startDate+"TO"+hourly_endDate+" MongoDB done");

            // Update lastMeasurementUpdate
            // System.out.print("LastMeasurementUpdate: "+ cityId+" BEFORE:"+cityRepository.findById(cityId).get().getLastMeasurementUpdate()+" -> AFTER:"+LocalDate.now().minusDays(1).atTime(23, 0));
            cityService.setLastMeasurementUpdateById(cityId, LocalDate.now().minusDays(1).atTime(23, 0));
            logger.info("cityService.setLastMeasurementUpdateById: cityId:"+cityId+" lastMeas.Update:"+LocalDate.now().minusDays(1).atTime(23, 0));

        } catch (NoSuchElementException e) {
            logger.error("Trying to refreshCityHistoricalData on not found cityId {}" , cityId);
        }
        catch(Exception ignored){

        }
    }

    public void refreshForecast() throws JsonProcessingException{
        //Make Sure no stale Forecast exists in DB
        forecastRedisService.deleteAllForecast();

        String[] regCodesList = new String[] {
                "lom", "emi", "cal", "sar", "umb", "aos", "lat", "cam", "apu", "lig",
                "tre", "mol", "sic", "ven", "pie", "tus", "the", "abr", "bas", "fri"
        };

        for(String code: regCodesList) {
            Set<String> cityIdList = jedisCluster.keys("city:{" + code + "}*");
            for (String cityId : cityIdList) {
                System.out.println(cityId.substring(6));
                refreshCityForecast(code + cityId.substring(10));
            }
        }
    }

    private void refreshCityForecast(String cityId) throws JsonProcessingException{
        CityDTO cityDTO = cityService.getCityWithID(cityId);
        // Get 7 days forecast and save
        APIResponseDTO apiResponseDTO = dataHarvestService.getCityForecast(cityDTO.getLatitude(), cityDTO.getLongitude(), 0, 7);
        HourlyMeasurementDTO hourlyMeasurementDTO = apiResponseDTO.getHourly();
        hourlyMeasurementDTO.setCityId(cityId);
        forecastRedisService.saveForecast(hourlyMeasurementDTO);
        System.out.println("refreshCityForecast: Done " + cityId);
    }


    /**
     * Initializes extreme weather events for all cities by invoking the update logic
     * for each city's identifier using the ExtremeWeatherEventService.
     */
    public void initializeExtremeWeatherEvents() {
        // Retrieve the list of all cities from the repository
        List<City> cities = cityRepository.findAll();

        // Iterate through each city and update its extreme weather events
        for (City city : cities) {
            try {
                extremeWeatherEventService.updateExtremeWeatherEventAutomatic(city.getId());
                System.out.println("ExtremeWeatherEvent updated for "+ city.getId() + "(" +city.getName() + ")");
                logger.info("ExtremeWeatherEvent updated for {} ({})", city.getId(), city.getName());
            }
            catch (ThresholdsNotPresentException e){
                logger.error("ExtremeWeatherEvent updating error for {}: ThresholdsNotPresentException ({})", city.getId(), city.getName());
            }
            catch (CityNotFoundException e){
                logger.error("ExtremeWeatherEvent updating error for {}: CityNotFoundException ({})", city.getId(), city.getName());
            }
        }
    }
}