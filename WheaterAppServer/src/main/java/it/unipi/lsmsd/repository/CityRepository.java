package it.unipi.lsmsd.repository;

import it.unipi.lsmsd.model.City;
import it.unipi.lsmsd.model.CityBasicProjection;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CityRepository extends MongoRepository<City, String> {
    List<City> findAllByName(String name);
    List<CityBasicProjection> findAllBy();
    List<CityBasicProjection> findAllByNameOrderByFollowers(String name);
    @Query("{ '_id': ?0, 'eweList': { $elemMatch: { 'dateEnd': null } } }")
    Optional<City> findCityWithOngoingEvents(String cityId);
    @Query("{ '_id': ?0, 'eweList': { $elemMatch: { 'category': ?1 } } }")
    Optional<City> findCityWithEventsOfCategory(String cityId, String category);
    @Query("{ '_id': ?0, 'eweList': { $elemMatch: { 'dateStart': { $lt: ?1 } } } }")
    Optional<City> findCityWithEventsStartedBefore(String cityId, java.time.LocalDateTime startTime);
    @Query("{ '_id': ?0, 'eweList': { $elemMatch: { 'category': ?1, 'dateStart': { $gte: ?2, $lte: ?3 } } } }")
    Optional<City> findCityWithEventsInRangeAndCategory(String cityId, String category, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
