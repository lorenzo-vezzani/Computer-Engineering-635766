package it.unipi.lsmsd.model;

public interface CityBasicProjection {
    String getId();
    String getName();
    String getRegion();
    double getLatitude();
    double getLongitude();
    int getElevation();
}
