package it.unipi.lsmsd.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// Handle the response of Open-Meteo API
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore the properites that are not included in the DTO
public class APIResponseDTO {
    private double latitude;
    private double longitude;
    private double elevation;
    
    @JsonProperty("hourly")
    private HourlyMeasurementDTO hourly;

    /***********************************/
    // NOTE: These fields are included in the Open-Meteo Response and could be possibly used if needed
    
    // @JsonProperty("generationtime_ms")
    // private double generationTimeMs;
    
    // @JsonProperty("utc_offset_seconds")
    // private int utcOffsetSeconds;
    
    // private String timezone;
    
    // @JsonProperty("timezone_abbreviation")
    // private String timezoneAbbreviation;

    /***********************************/

    // Getters and Setters
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getElevation() { return elevation; }
    public HourlyMeasurementDTO getHourly() { return hourly; }

    public static APIResponseDTO merge(APIResponseDTO a, APIResponseDTO b) {
        if (a == null) return b;
        if (b == null) return a;

        APIResponseDTO merged = new APIResponseDTO();

        merged.latitude = a.latitude;
        merged.longitude = a.longitude;
        merged.elevation = a.elevation;

        // Merge Hourly Measurements
        merged.hourly = HourlyMeasurementDTO.merge(a.hourly, b.hourly);

        return merged;
    }

}
