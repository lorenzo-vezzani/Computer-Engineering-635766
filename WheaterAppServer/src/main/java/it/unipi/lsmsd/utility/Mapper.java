package it.unipi.lsmsd.utility;

import it.unipi.lsmsd.DTO.APIResponseDTO;
import it.unipi.lsmsd.DTO.CityDTO;
import it.unipi.lsmsd.DTO.HourlyMeasurementDTO;
import it.unipi.lsmsd.model.City;
import it.unipi.lsmsd.model.HourlyMeasurement;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

//TODO: Use MapStruct to Map faster and better

public final class Mapper {
    // Single reusable instance of objectMapper throughout the application's lifecycle
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Private constructor to prevent instantiation
    private Mapper() { }

    // Extracts hourly weather data from the JSON string into a APIResponseDTO
    public static APIResponseDTO mapAPIResponse(String json) throws JsonProcessingException{
        return objectMapper.readValue(json, APIResponseDTO.class);
    }

    // Extracts list of cityDTO from the JSON string 
    public static List<CityDTO> mapCityList(String json) throws IOException{
        // Extract the "results" array from {  "results": [ { "id": 3170647, "name": "Pisa", ... }],"generationtime_ms": 2.6580095 }
        JsonNode root = objectMapper.readTree(json); 
        JsonNode resultsNode = root.get("results");
        // Map to the List of CityDTO
        return objectMapper.readerForListOf(CityDTO.class).readValue(resultsNode);
    }

    // Maps HourlyMeasurementDTO to List<HourlyMeasurement>
    public static List<HourlyMeasurement> mapHourlyMeasurement(HourlyMeasurementDTO dto) {
        List<HourlyMeasurement> measurements = new ArrayList<>();
        
        List<String> times = dto.getTime();
        List<Double> temperatures = dto.getTemperature();
        List<Double> rains = dto.getRain();
        List<Double> snowfalls = dto.getSnowfall();
        List<Double> windSpeeds = dto.getWindspeed();

        int dataSize = times.size(); // Assuming all lists have the same size

        for (int i = 0; i < dataSize; i++) {
            HourlyMeasurement measurement = new HourlyMeasurement();
            
            measurement.setCityId(dto.getCityId());
            measurement.setTime(ISODateUtil.getISODate(times.get(i))); //"2025-03-15T00:00" -> 2025-03-15T00:00:00.000+00:00
            measurement.setTemperature(temperatures.get(i));
            measurement.setRainfall(rains.get(i));
            measurement.setSnowfall(snowfalls.get(i));
            measurement.setWindSpeed(windSpeeds.get(i));
            measurements.add(measurement);
        }

        return measurements;
    }
    
    // Maps List<HourlyMeasurement> to HourlyMeasurementDTO
    public static HourlyMeasurementDTO mapHourlyMeasurementDTO(List<HourlyMeasurement> hourlyMeasurements){
        HourlyMeasurementDTO hourlyMeasurementDTO = new HourlyMeasurementDTO();
    
        List<String> times = new ArrayList<>();
        List<Double> temperatures = new ArrayList<>();
        List<Double> rains = new ArrayList<>();
        List<Double> snowfalls = new ArrayList<>();
        List<Double> windSpeeds = new ArrayList<>();
        
        // Assuming the first measurement has the cityId
        if (!hourlyMeasurements.isEmpty()) {
            hourlyMeasurementDTO.setCityId(hourlyMeasurements.get(0).getCityId());
        }
        
        // Iterate over hourly measurements and fill the DTO lists
        for (HourlyMeasurement measurement : hourlyMeasurements) {
            // NOTE : This step to convert the time to UTC+0 was necessary because Java query to the MongoDB
            //          gets the date in the local timezone. To standarize we ensure the date is always in UTC+0 
            // Convert the Date to Instant (UTC)
            Instant utcInstant = measurement.getTime().toInstant();
            // Format the Instant as a string in UTC
            String utcTimeString = utcInstant.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME);
            // Add the formatted UTC time string to your list
            times.add(utcTimeString);

            temperatures.add(measurement.getTemperature());
            rains.add(measurement.getRainfall());
            snowfalls.add(measurement.getSnowfall());
            windSpeeds.add(measurement.getWindSpeed());
        }
        
        hourlyMeasurementDTO.setTime(times);
        hourlyMeasurementDTO.setTemperature(temperatures);
        hourlyMeasurementDTO.setRain(rains);
        hourlyMeasurementDTO.setSnowfall(snowfalls);
        hourlyMeasurementDTO.setWindspeed(windSpeeds);
        
        return hourlyMeasurementDTO;
    }

    // Maps cityDTO to city
    public static City mapCity(CityDTO cityDTO){
        
        // Get the required fields for cityID generation
        String name = cityDTO.getName();
        String region =  cityDTO.getRegion();
        Double latitude = cityDTO.getLatitude(); 
        Double longitude = cityDTO.getLongitude();
        // generate custom city Id
        String cityId = cityDTO.get_id() != null ? cityDTO.get_id() : CityUtility.generateCityId(name, region, latitude, longitude);
        //TODO map StartDate, EndDate and EWE threshold as well
        // Map the cityDTO to city
        return new City(cityId, name, region, latitude, longitude, cityDTO.getElevation(),0);
    }

    // Maps cityDTO with thresholds to city
    public static City mapCityWithThresholds(CityDTO cityDTO){

        // Get the required fields for cityID generation
        String name = cityDTO.getName();
        String region =  cityDTO.getRegion();
        Double latitude = cityDTO.getLatitude();
        Double longitude = cityDTO.getLongitude();
        // generate custom city Id
        String cityId = CityUtility.generateCityId(name, region , latitude, longitude);
        // Map the cityDTO to city
        return new City(cityId, name, region, latitude, longitude, cityDTO.getElevation(),0,cityDTO.getEweThresholds());
    }
    
    // Maps city to cityDTO
    public static CityDTO mapCityDTO(City city){
        CityDTO cityDTO = new CityDTO();
        // Map each required fields
        String cityId = cityDTO.get_id() != null ? cityDTO.get_id() : 
            CityUtility.generateCityId(city.getName(), city.getRegion(), city.getLatitude(), city.getLongitude());
        cityDTO.set_id(cityId);
        cityDTO.setName(city.getName());
        cityDTO.setRegion(city.getRegion());
        cityDTO.setLatitude(city.getLatitude());
        cityDTO.setLongitude(city.getLongitude());
        cityDTO.setElevation(city.getElevation());
        // TODO: Saving the date in format 2025-05-01:23:00 instead of "Thu May 01 23:00:00 UTC 2025"
        cityDTO.setStartDate(city.getStartDate()!=null?city.getStartDate().toString():null);
        // TODO: Saving the date in format 2025-05-01:23:00 instead of "Thu May 01 23:00:00 UTC 2025"
        cityDTO.setEndDate(city.getEndDate()!=null?city.getEndDate().toString():null);
        cityDTO.setEweThresholds(city.getEweThresholds());
        return cityDTO;
    }

    
}
