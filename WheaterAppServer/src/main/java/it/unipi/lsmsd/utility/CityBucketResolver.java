package it.unipi.lsmsd.utility;

import it.unipi.lsmsd.exception.BucketException;
import it.unipi.lsmsd.exception.BucketNotDefinedForRegionException;
import it.unipi.lsmsd.exception.RegionCodeNotRecognizedException;

import java.util.*;

public class CityBucketResolver {

    private static final Map<String, String> regionCodeToName = Map.ofEntries(
            Map.entry("lom", "Lombardy"),
            Map.entry("emi", "Emilia-Romagna"),
            Map.entry("cal", "Calabria"),
            Map.entry("sar", "Sardinia"),
            Map.entry("umb", "Umbria"),
            Map.entry("aos", "Aosta Valley"),
            Map.entry("lat", "Lazio"),
            Map.entry("cam", "Campania"),
            Map.entry("apu", "Apulia"),
            Map.entry("lig", "Liguria"),
            Map.entry("tre", "Trentino-Alto Adige"),
            Map.entry("mol", "Molise"),
            Map.entry("sic", "Sicily"),
            Map.entry("ven", "Veneto"),
            Map.entry("pie", "Piedmont"),
            Map.entry("tus", "Tuscany"),
            Map.entry("the", "The Marches"),
            Map.entry("abr", "Abruzzo"),
            Map.entry("bas", "Basilicata"),
            Map.entry("fri", "Friuli-Venezia Giulia")
    );

    private static final Set<String> bucket1 = Set.of(
            "Lombardy", "Trentino-Alto Adige", "Veneto", "Friuli-Venezia Giulia", "Piedmont", "Aosta Valley"
    );

    private static final Set<String> bucket2 = Set.of(
            "Liguria", "Tuscany", "Emilia-Romagna", "Umbria", "The Marches", "Lazio", "Abruzzo"
    );

    private static final Set<String> bucket3 = Set.of(
            "Sicily", "Sardinia", "Molise", "Apulia", "Calabria", "Campania", "Basilicata"
    );

    public static String getBucket(String cityId) throws BucketException {
        String[] parts = cityId.split("-");
        if (parts.length < 2) {
            throw new BucketException("CityId string has fewer than 2 parts");
        }

        String regionCode = parts[0].toLowerCase(); // e.g., "tus"
        String region = regionCodeToName.get(regionCode);

        if (region == null) {
            throw new RegionCodeNotRecognizedException("Region code: " + regionCode);
        }

        if (bucket1.contains(region)) return "bucket1";
        if (bucket2.contains(region)) return "bucket2";
        if (bucket3.contains(region)) return "bucket3";

        throw new BucketNotDefinedForRegionException("Bucket not defined for region: " + regionCode + " - region: " + region);
    }

    public static String getRegionFromId(String cityId){
        String[] parts = cityId.split("-");
        if (parts.length < 2) {
            throw new BucketException("CityId string has fewer than 2 parts");
        }

        String regionCode = parts[0].toLowerCase();
        return regionCodeToName.get(regionCode);
    }

    public static String getIdFromRegion(String region) {
        for (Map.Entry<String, String> entry : regionCodeToName.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(region)) {
                return entry.getKey();
            }
        }
        throw new IllegalArgumentException("Region name is not valid");
    }

}