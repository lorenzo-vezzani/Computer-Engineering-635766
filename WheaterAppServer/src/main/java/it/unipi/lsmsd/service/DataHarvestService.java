package it.unipi.lsmsd.service;

import it.unipi.lsmsd.DTO.APIResponseDTO;
import it.unipi.lsmsd.DTO.CityDTO;
import it.unipi.lsmsd.utility.Mapper;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import io.github.resilience4j.retry.annotation.Retry;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
// Hit the Open Meteo API to retrieve Weather Data
@Service
public class DataHarvestService {

    // Base Url of Open Meteo to retrive data from
    private final RestTemplate restTemplate;
    private static final String API_URL_HISTORY = "https://archive-api.open-meteo.com/v1/archive";
    private static final String API_URL_FORECAST = "https://api.open-meteo.com/v1/forecast";
    private static final String API_URL_GEOCODING = "https://geocoding-api.open-meteo.com/v1/search";

    // Constructor with REST config
    public DataHarvestService() {
        //Configure timeouts by setting the request factory
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // Maximum time 5s to establish a connection with the server
        factory.setReadTimeout(5000); //Maximum time 5s to wait for a response after the connection is established
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Retrieves a complete set of meteorological data (historical and forecasted) for the given location and date range,
     * by delegating to the Open-Meteo API archive and forecast endpoints.
     * <p>
     * The method splits the requested date interval into two parts:
     * <ul>
     *   <li>A historical segment: from {@code startDate} to two days ago (maximum allowed by the archive endpoint)</li>
     *   <li>A forecast segment: from one day ago to {@code endDate}</li>
     * </ul>
     * It then invokes the corresponding endpoints and merges the results into a single {@link APIResponseDTO}.
     * </p>
     *
     * @param latitude  the latitude of the location
     * @param longitude the longitude of the location
     * @param startDate the start of the time range (inclusive), in {@code YYYY-MM-DD} format
     * @param endDate   the end of the time range (inclusive), in {@code YYYY-MM-DD} format
     * @return an {@link APIResponseDTO} containing combined data from both archive and forecast endpoints
     * @throws JsonProcessingException if an error occurs while mapping either API response
     */
    @Retry(name = "OpenMeteoApiRetry")
    public APIResponseDTO getCityHistoricalMeasurement(double latitude, double longitude, String startDate, String endDate) throws JsonProcessingException {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        LocalDate today = LocalDate.now();
        LocalDate archiveMax = today.minusDays(2);   // Up to two days ago
        LocalDate forecastMin = today.minusDays(1);  // From one day ago onward

        APIResponseDTO archiveDTO = null;
        APIResponseDTO forecastDTO = null;

        if (!start.isAfter(archiveMax)) {
            // Historical segment: [start, min(end, archiveMax)]
            LocalDate archiveEnd = end.isBefore(archiveMax) ? end : archiveMax;
            archiveDTO = getCityHistoricalMeasurement_archive(latitude, longitude, start.toString(), archiveEnd.toString());
        }

        if (!end.isBefore(forecastMin)) {
            // Forecast segment: [max(start, forecastMin), end]
            LocalDate forecastStart = start.isAfter(forecastMin) ? start : forecastMin;
            forecastDTO = getCityHistoricalMeasurement_forecast(latitude, longitude, forecastStart.toString(), end.toString());
        }

        // Merge results
        if (archiveDTO != null && forecastDTO != null) {
            return APIResponseDTO.merge(archiveDTO, forecastDTO);
        } else if (archiveDTO != null) {
            return archiveDTO;
        } else if (forecastDTO != null) {
            return forecastDTO;
        } else {
            // Interval outside valid range (e.g., entirely before archive limit or invalid dates)
            return new APIResponseDTO(); // Or handle as appropriate
        }
    }


    /**
     * Retrieves historical meteorological data (temperature, rain, snowfall, and wind speed) for a given city
     * using the Open-Meteo API archive endpoint.
     * <p>
     * The request is made for a specific geographic location (latitude and longitude) and a time interval
     * defined by {@code startDate} and {@code endDate}, which must both be in the format {@code YYYY-MM-DD}
     * and cannot include dates more recent than two days before the current day (e.g., if today is May 12,
     * the maximum allowable date is May 10).
     * </p>
     *
     * @param latitude  the latitude of the location
     * @param longitude the longitude of the location
     * @param startDate the start date of the historical range (inclusive), in {@code YYYY-MM-DD} format
     * @param endDate   the end date of the historical range (inclusive), in {@code YYYY-MM-DD} format
     * @return an {@link APIResponseDTO} containing the mapped weather data for the specified range and location
     * @throws JsonProcessingException if an error occurs while mapping the API response to the DTO
     */
    @Retry(name = "OpenMeteoApiRetry")
    private APIResponseDTO getCityHistoricalMeasurement_archive(double latitude, double longitude, String startDate, String endDate) throws JsonProcessingException{
        // Append the parameters (Hourly Measurements of Temperature_2m, Rain, Snowfall and Wind_speed_10m) to the base URL
        String url = String.format(Locale.US, "%s?latitude=%f&longitude=%f&start_date=%s&end_date=%s&hourly=temperature_2m,rain,snowfall,wind_speed_10m",
                API_URL_HISTORY, latitude, longitude, startDate, endDate);
        ResponseEntity<String> apiResponse = restTemplate.getForEntity(url, String.class);

        // Handle Unsucessful Requests
        checkAPIresponse(apiResponse);

        // On success get the data and Map to the DTO
        APIResponseDTO responseDTO = Mapper.mapAPIResponse(apiResponse.getBody());
        return responseDTO;
    }

    /**
     * Retrieves both forecasted and recent past meteorological data (temperature, rain, snowfall, and wind speed)
     * for a specified location using the Open-Meteo API forecast endpoint.
     * <p>
     * The request is parameterized using {@code startDate} and {@code endDate}, from which the number of forecast
     * days and past days is calculated relative to the current date.
     * The dates must be in {@code YYYY-MM-DD} format.
     * </p>
     *
     * @param latitude  the latitude of the target location
     * @param longitude the longitude of the target location
     * @param startDate the start date (inclusive) of the time interval, in {@code YYYY-MM-DD} format
     * @param endDate   the end date (inclusive) of the time interval, in {@code YYYY-MM-DD} format
     * @return an {@link APIResponseDTO} containing the weather forecast and historical data for the given location and date range
     * @throws JsonProcessingException if the mapping of the API response to the DTO fails
     */
    @Retry(name = "OpenMeteoApiRetry")
    private APIResponseDTO getCityHistoricalMeasurement_forecast(double latitude, double longitude, String startDate, String endDate) throws JsonProcessingException{
        // Calculate forecastDays and pastDays
        LocalDate today = LocalDate.now();
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        int pastDays = 0;
        int forecastDays = 0;

        if (start.isBefore(today)) {
            pastDays = (int) ChronoUnit.DAYS.between(start, today);
        }

        if (end.isAfter(today)) {
            forecastDays = (int) ChronoUnit.DAYS.between(today.minusDays(1), end);
        }

        // Append the parameters (Hourly Measurements of Temperature_2m, Rain, Snowfall and Wind_speed_10m) to the base URL
        String url = String.format(Locale.US, "%s?latitude=%f&longitude=%f&hourly=temperature_2m,rain,snowfall,wind_speed_10m&forecast_days=%d&past_days=%d",
                API_URL_FORECAST, latitude, longitude, forecastDays, pastDays);

        ResponseEntity<String> apiResponse = restTemplate.getForEntity(url, String.class);

        // Handle Unsucessful Requests
        checkAPIresponse(apiResponse);

        // On success get the data and Map to the DTO
        APIResponseDTO responseDTO = Mapper.mapAPIResponse(apiResponse.getBody());
        return responseDTO;
    }

    // Get the forecast of the given city
    // https://api.open-meteo.com/v1/forecast?latitude=43.7085&longitude=10.4036&hourly=temperature_2m,rain,snowfall,wind_speed_10m&forecast_days=7&past_days=1
    @Retry(name="OpenMeteoApiRetry")
    public APIResponseDTO getCityForecast (double latitude, double longitude, int pastDays, int forecastDays) throws JsonProcessingException {
        // create url for API
        String url = String.format(Locale.US, "%s?latitude=%f&longitude=%f&hourly=temperature_2m,rain,snowfall,wind_speed_10m&forecast_days=%d&past_days=%d",
            API_URL_FORECAST, latitude, longitude, forecastDays, pastDays);
        // API call and Response
        ResponseEntity<String> apiResponse = restTemplate.getForEntity(url, String.class);

        // Handle Unsucessful Requests
        checkAPIresponse(apiResponse);

        // On success get the data and Map to the DTO
        APIResponseDTO responseDTO = Mapper.mapAPIResponse(apiResponse.getBody());
        return responseDTO;
    }
    //
    @Retry(name="OpenMeteoApiRetry")
    public CityDTO getCity(String name, String countryCode) throws IOException{
        // create url for API
        String url = String.format(Locale.US, "%s?name=%s&countryCode=%s", API_URL_GEOCODING, name, countryCode);
        ResponseEntity<String> apiResponse = restTemplate.getForEntity(url, String.class);
        // Handle Unsucessful Requests
        checkAPIresponse(apiResponse);
        // On success get the data and Map to the DTO
        List<CityDTO> cityDTO = Mapper.mapCityList(apiResponse.getBody());
        // Get the first element from the list --> Assumption that usually only 1 element and if multiple the first one matches the name exactly
        return cityDTO.get(0);
    }

    // Handle Unsucessful Requests
    private void checkAPIresponse(ResponseEntity<String> apiResponse) {
        HttpStatusCode statusCode = apiResponse.getStatusCode();

        if (apiResponse == null || apiResponse.getBody() == null || apiResponse.getBody().trim().isEmpty()) {
            throw new IllegalArgumentException("Open-Meteo API response is null or empty");
        } else if (statusCode.is5xxServerError()) {
            //Server error 500-599 to be retried (Handled by Resilence4j's config in application.yml)
            throw new HttpServerErrorException(statusCode, "Open-Meteo Server Error: " + apiResponse.getBody());
        } else if (statusCode.is4xxClientError()) {
            // No retry
            throw new HttpClientErrorException(statusCode, "Open-Meteo Client error: " + apiResponse.getBody());
        } else if (!statusCode.is2xxSuccessful()) {
            // No retry
            throw new RestClientException(
                    "Open-Meteo Request failed, Status Code: " + statusCode.value() + ", Error: " + apiResponse.getBody());
        }
    }
    
}