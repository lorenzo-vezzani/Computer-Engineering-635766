package it.unipi.lsmsd.model;

import org.springframework.data.annotation.Id;

public class EWEThreshold {
    @Id
    private String id;
    double rainfall;
    double snowfall;
    double maxTemperature;
    double minTemperature;
    double windSpeed;

    // Constructors
    public EWEThreshold(double rainfall, double snowfall, double maxTemperature, double minTemperature, double windSpeed) {
        this.rainfall = rainfall;
        this.snowfall = snowfall;
        this.maxTemperature = maxTemperature;
        this.minTemperature = minTemperature;
        this.windSpeed = windSpeed;
    }

    // Getters
    public double getRainfall() { return rainfall; }
    public double getSnowfall() { return snowfall; }
    public double getMaxTemperature() { return maxTemperature; }
    public double getMinTemperature() { return minTemperature; }
    public double getWindSpeed() { return windSpeed; }

    // Setters
    public void setRainfall(double rainfall) { this.rainfall = rainfall; }
    public void setSnowfall(double snowfall) { this.snowfall = snowfall; }
    public void setMaxTemperature(double maxTemperature) { this.maxTemperature = maxTemperature; }
    public void setMinTemperature(double minTemperature) { this.minTemperature = minTemperature; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }

    @Override
    public String toString() {
        return "EWEThreshold{" +
                "rainfall=" + rainfall +
                ", snowfall=" + snowfall +
                ", maxTemperature=" + maxTemperature +
                ", minTemperature=" + minTemperature +
                ", windSpeed=" + windSpeed +
                '}';
    }
}

