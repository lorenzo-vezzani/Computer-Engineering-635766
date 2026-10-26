package it.unipi.lsmsd.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "extreme_weather_events")
public class ExtremeWeatherEvent {

    // Attributes
    @Id
    private String id;
    private String cityId;
    private ExtremeWeatherEventCategory category;
    private Integer strength;
    private LocalDateTime dateStart;
    private LocalDateTime dateEnd;


    // Constructors
    public ExtremeWeatherEvent() {}

    public ExtremeWeatherEvent(String id, String cityId, ExtremeWeatherEventCategory category, Integer strength, Integer radius, LocalDateTime dateStart, LocalDateTime dateEnd) {
        this.id = id;
        this.cityId = cityId;
        this.category = category;
        this.strength = strength;
        this.dateStart = dateStart;
        this.dateEnd = dateEnd;
    }

    // Getters and setters
    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getCityId() { return cityId; }

    public ExtremeWeatherEventCategory getCategory() { return category; }

    public void setCategory(ExtremeWeatherEventCategory category) { this.category = category; }

    public Integer getStrength() { return strength; }

    public void setStrength(Integer strength) { this.strength = strength; }

    public LocalDateTime getDateStart() { return dateStart; }

    public void setDateStart(LocalDateTime dateStart) { this.dateStart = dateStart; }

    public LocalDateTime getDateEnd() { return dateEnd; }

    public void setDateEnd(LocalDateTime dateEnd) { this.dateEnd = dateEnd; }

}
