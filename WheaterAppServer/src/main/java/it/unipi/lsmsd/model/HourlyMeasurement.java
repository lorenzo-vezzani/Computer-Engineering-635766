package it.unipi.lsmsd.model;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// Time-series Collection
@Document(collection = "hourly_measurements")
public class HourlyMeasurement {
    @Id
    private String id; // MongoDB auto generated
    private String cityId; // Metadata field (metaField)
    private Date time; // Measurement time in UTC in ISO format
    private Double temperature;
    private Double rainfall;
    private Double snowfall;
    private Double windSpeed;
    
    // Getters
    public String getId() { return id; }
    public String getCityId() { return cityId; }
    public Date getTime() { return time; }
    public Double getTemperature() { return temperature; }
    public Double getRainfall() { return rainfall; }
    public Double getSnowfall() { return snowfall; }
    public Double getWindSpeed() { return windSpeed; }
    
    //Setters
    public void setId(String id) { this.id = id; }
    public void setCityId(String cityId) { this.cityId = cityId; }
    public void setTime(Date time) { this.time = time; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public void setRainfall(Double rainfall) { this.rainfall = rainfall; }
    public void setSnowfall(Double snowfall) { this.snowfall = snowfall; }
    public void setWindSpeed(Double windSpeed) { this.windSpeed = windSpeed; }


    
    
}
