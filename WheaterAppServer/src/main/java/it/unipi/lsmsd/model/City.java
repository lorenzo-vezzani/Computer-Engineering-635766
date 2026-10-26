package it.unipi.lsmsd.model;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document(collection = "cities")
public class City {
    @Id
    private String id; // MongoDB Id which is a custom id 
    private String name;
    private String region;
    private Double latitude;
    private Double longitude;
    private Double elevation;
    private Integer followers;
    private Date startDate;
    private Date endDate;
    public void setStartDate(Date startTime) {
        this.startDate = startTime;
    }
    public void setEndDate(Date endTime) {
        this.endDate = endTime;
    }
    private EWEThreshold eweThresholds;
    public Date getStartDate() {
        return startDate;
    }
    public Date getEndDate() {
        return endDate;
    }
    private LocalDateTime lastEweUpdate;
    private LocalDateTime lastMeasurementUpdate;
    private List<ExtremeWeatherEvent> eweList;

    // Constructors   
    public City(){}
    public City(String id, String name, String region, Double latitude, Double longitude, Double elevation,
            Integer followers) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevation = elevation;
        this.followers = followers;
        this.eweList = new ArrayList<>();
    }

    public City(String id, String name, String region, Double latitude, Double longitude, Double elevation,
                Integer followers, EWEThreshold eweThresholds) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevation = elevation;
        this.followers = followers;
        this.eweThresholds = eweThresholds;
        this.eweList = new ArrayList<>();
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getRegion() { return region; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Double getElevation() { return elevation; }
    public Integer getFollowers() { return followers; }
    public EWEThreshold getEweThresholds() { return eweThresholds; }
    public LocalDateTime getLastEweUpdate() { return lastEweUpdate; }
    public LocalDateTime getLastMeasurementUpdate() { return lastMeasurementUpdate; }
    public List<ExtremeWeatherEvent> getEweList() {return eweList; };

    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setRegion(String country) { this.region = country; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setElevation(Double elevation) { this.elevation = elevation; }
    public void setFollowers(Integer followers) { this.followers = followers; }
    public void setEweThresholds(EWEThreshold eweThreshold) { this.eweThresholds = eweThreshold; }
    public void setLastEweUpdate(LocalDateTime lastEweUpdate) { this.lastEweUpdate = lastEweUpdate; }
    public void setLastMeasurementUpdate(LocalDateTime lastMeasurementUpdate) { this.lastMeasurementUpdate = lastMeasurementUpdate; }
    public void setEweList(List<ExtremeWeatherEvent> eweList) {this.eweList = eweList; }

}
