package it.unipi.lsmsd.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import redis.clients.jedis.Jedis;

import it.unipi.lsmsd.model.EWEThreshold;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.unipi.lsmsd.DTO.APIResponseDTO;
import it.unipi.lsmsd.DTO.CityDTO;
import it.unipi.lsmsd.DTO.HourlyMeasurementDTO;
import it.unipi.lsmsd.utility.MongoInitializer;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

import static it.unipi.lsmsd.utility.StatisticsUtility.getEweThresholdsFromMeasurements;

@Service
public class DataInitializeService {

    @Autowired
    private CityService cityService;
    @Autowired
    private HourlyMeasurementService hourlyMeasurementService;
    @Autowired
    private JedisCluster jedisCluster;

    private static final double PERCENTILE = 0.075;

    private static final Logger logger = LoggerFactory.getLogger(MongoInitializer.class);
    private final ObjectMapper objectMapper;

    public DataInitializeService(){
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
  
    public void initializeCitiesMongo() throws IOException{
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:data_init/cities.json");
        try (InputStream is = resource.getInputStream()){
            List<CityDTO> cities = objectMapper.readValue(is, new TypeReference<List<CityDTO>>() {});
            // Save to the MondoDB
            cityService.saveCities(cities);
        }
    }

    public void initializeCitiesRedis() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:data_init/cities.json");

        try (InputStream is = resource.getInputStream()) {
            List<CityDTO> cities = objectMapper.readValue(is, new TypeReference<List<CityDTO>>() {});

            for (CityDTO c : cities) {
                Map<String, String> cityFields = new HashMap<>();
                cityFields.put("name", c.getName());
                cityFields.put("region", c.getRegion());
                cityFields.put("elevation", c.getElevation().toString());

                String cityKey = String.format("city:{%s}%s", c.get_id().substring(0, 3), c.get_id().substring(3));

                String regionSetKey = String.format("region:{%s}", c.get_id().substring(0, 3));

                jedisCluster.hset(cityKey, cityFields);
                jedisCluster.sadd(regionSetKey, cityKey);
            }
        }
    }


    public void initializeMeasurements() throws IOException {
        // Finds all JSON files under resources/data_init/measurements/
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/data_init/measurements/*.json");

        // Use a thread pool with max 4 threads or number of available CPUs (whichever is lower)
        int threadCount = Math.min(4, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        // Submit a separate task for each file to process in parallel
        for (Resource resource : resources) {
            // processFile(resource);
            futures.add(executor.submit(() -> processFile(resource)));
        }

        // Wait for all threads to finish execution
        for (Future<?> future : futures) {
            try {
                future.get(); // Will block until each task is done
            } catch (InterruptedException | ExecutionException e) {
                // Log and continue if any task fails
                logger.error("Error in file processing task: " + e.getMessage());
            }
        }

        // Cleanly shut down the executor
        executor.shutdown();
    }

    
    // Processes a single JSON file: parses, extracts cityId, and saves data to MongoDB.
    private void processFile(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            // Deserialize JSON into DTO
            APIResponseDTO responseDTO = objectMapper.readValue(is, new TypeReference<APIResponseDTO>() {});
            HourlyMeasurementDTO dto = responseDTO.getHourly();
            
            // Use the filename (e.g. pis-tus-43.7085-10.4036.json) as the cityId
            String cityId = Optional.ofNullable(resource.getFilename())
                    .map(name -> name.replace(".json", ""))
                    .orElseThrow(() -> new IllegalArgumentException("City ID cannot be determined from filename: "));

            logger.info("Initializing measurements of city: " + cityId);
            System.out.println("Initializing measurements of city: " + cityId);

            dto.setCityId(cityId);
            // Save the parsed measurement to MongoDB
            hourlyMeasurementService.saveHourlyMeasurements(dto);

            // Computes the EWE Thresholds
            EWEThreshold cityEweThresholds = getEweThresholdsFromMeasurements(dto, PERCENTILE);
            logger.info("EWE Thresholds calculated for "+cityId+": "+cityEweThresholds.toString());

            cityService.updateCityThresholds(cityId, cityEweThresholds);
            
            // Save the first and last element of the time as timeframe of the historical data
            List<String> timeList = dto.getTime(); 
            String endDate = timeList.get(timeList.size() - 1);
            String startDate = timeList.get(0);
            // Update the city Last Update date
            cityService.updateStartEndDate(startDate, endDate, cityId);
            // System.out.println("cityService.updateStartEndDate: cityId:"+cityId+" startDate:"+startDate+" endDate:"+endDate);

            // Update lastMeasurementUpdate
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime endDate_toLDT = LocalDateTime.parse(endDate, formatter);
            cityService.setLastMeasurementUpdateById(cityId, endDate_toLDT);
            // System.out.println("cityService.setLastMeasurementUpdateById: cityId:"+cityId+" lastMas.Update:"+endDate_toLDT);

            logger.info("Added HourlyMeasurement for "+cityId+" and updated city.endDate = "+ endDate);

        } catch (IOException e) {
            // Log the error but don't stop the process
            logger.error("Failed to process " + resource.getFilename() + ": " + e.getMessage());
        }
    }
}
