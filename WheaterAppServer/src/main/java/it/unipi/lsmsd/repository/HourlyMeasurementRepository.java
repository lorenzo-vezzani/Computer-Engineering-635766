package it.unipi.lsmsd.repository;

import it.unipi.lsmsd.model.HourlyMeasurement;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface HourlyMeasurementRepository extends MongoRepository<HourlyMeasurement, String> {
    @Query("{ 'cityId' : ?0, 'time' : { $gte: ?1, $lte: ?2 } }")
    List<HourlyMeasurement> findByCityIdAndTimeBetweenOrderByTimeTimeAsc(String cityId, Date startTime, Date endTime);

    Optional<HourlyMeasurement> findFirstByCityIdOrderByTimeAsc(String cityId);
    
    void deleteByCityIdAndTimeBetween(String cityId, Date startTime, Date endTime);

}

