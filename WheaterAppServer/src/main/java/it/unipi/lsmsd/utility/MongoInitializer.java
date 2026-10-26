package it.unipi.lsmsd.utility;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

// Spring Boot CommandLineRunner allows custom logic execution at startup
// It executes code after the application context is loaded and right before the application starts
@Component
public class MongoInitializer implements CommandLineRunner {

    private final MongoClient mongoClient;
    private static final Logger logger = LoggerFactory.getLogger(MongoInitializer.class);

    // Get database and collection names from application.yml
    @Value("${spring.data.mongodb.database}") 
    private String databaseName;
    @Value("${spring.mongo.measurement-collection}")
    private String measurementCollectionName;
    @Value("${spring.mongo.city-collection}")
    private String cityCollectionName;
    @Value("${spring.mongo.user-collection}")
    private String userCollectionName;

    public MongoInitializer(MongoClient mongoClient) { this.mongoClient = mongoClient; }

    // Checks if the all collection exists and creates it if it doesn’t
    @Override
    public void run(String... args) throws Exception {
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        List<String> mongoCollectionList=  database.listCollectionNames().into(new ArrayList<>());

        try {
            
            // Check if cityCollection exits
            if (!mongoCollectionList.contains(cityCollectionName)) {
                database.createCollection(cityCollectionName);
                // log
                logger.info("MongoDB: Collection '" + cityCollectionName + "' has been created");
            } 
                        
            // Create Collection if not in DB
            if (!mongoCollectionList.contains(measurementCollectionName)) {
                database.createCollection(measurementCollectionName);
                // Create Unique index with cityId aand time
                // Ordered in ascending order first by cityId and then by time
                database.getCollection(measurementCollectionName).createIndex(Indexes.ascending("cityId", "time"),new IndexOptions().unique(true));
                // log
                logger.info("MongoDB: Collection '" + measurementCollectionName + "' has been created");

            }
            
            // Check for the user collection
            if (!mongoCollectionList.contains(userCollectionName)) {
                database.createCollection(userCollectionName);
                MongoCollection<Document> userColl = database.getCollection(userCollectionName);
                // Create unique index for users collection with username
                userColl.createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
                // log
                logger.info("MongoDB: Collection '" + userCollectionName + "' has been created");
            }

        } catch (com.mongodb.MongoException ex) {
            logger.error("MongoDB Initializer: MongoDB error occurred: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.error("MongoDB Initializer: Invalid argument: " + ex.getMessage());
        } catch (Exception ex) {
            logger.error("MongoDB Initializer: An unexpected error occurred: " + ex.getMessage());
        }

    }
}