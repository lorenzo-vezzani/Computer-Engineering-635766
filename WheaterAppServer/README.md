# Weather Management Application

Project for the **Large Scale and Multi-Structured Databases** course, MSc Artificial Intelligence and Data Engineering / MSc Computer Engineering, University of Pisa (A.Y. 2024/25).

**Authors:** Lorenzo Vezzani, Rojan Shrestha, Alessio Simoncini
**Repository:** https://github.com/AlessioSmn/WheaterAppServer/tree/vmCluster

## Description

A server-side weather service for the most relevant and populous Italian cities, combining:

- **Forecasts:** daily and 7-day forecasts for stored cities, plus estimates for arbitrary locations.
- **Historical analytics:** aggregations over hourly measurements (temperature, rainfall, snowfall, wind speed), per city and per region.
- **Extreme Weather Events (EWE):** rainstorms, snowstorms, hurricanes, heatwaves and coldwaves, detected by comparing measurements against per-city thresholds.

The client is not implemented (as per specifications). The server exposes a REST API built with **Java and Spring**.

## Actors

| Actor | Capabilities |
|---|---|
| **Guest** | Sign up, view forecasts, historical data and EWEs |
| **Registered user** | Everything a guest can do, plus log in/out and follow/unfollow cities |
| **Admin** | Everything above, plus add cities, trigger data updates and edit EWE thresholds |

Authentication uses a **session token** (stored in Redis) that must be sent with protected requests and also identifies the role.

## Data sources

All data comes from the [Open-Meteo](https://open-meteo.com/) APIs, in JSON format:

- Historical (from 1940): `https://archive-api.open-meteo.com/v1/archive`
- Forecast: `https://api.open-meteo.com/v1/forecast`

## Database architecture

The main non-functional requirement is **fast reads and high availability**, so the application uses two NoSQL databases.

### MongoDB (document DB): analytics and persistent data

| Collection | Content | Modeling |
|---|---|---|
| `users` | `username` (unique), `password` (encrypted), `email` (unique), `listCityId` | Linking (city ids only) |
| `cities` | name, region, coordinates, thresholds, followers, `cityId`, `eweList` | **Embedding** of EWEs (rare events, documents stay small) |
| `measurements` | hourly temperature, rainfall, snowfall, windspeed, timestamp, `cityId` | **Linking**, to avoid approaching the 16 MB document limit |

**Indexes:** `username` on users; default `_id` on cities; compound `(cityId, time)` on measurements (costly in storage, but it makes analytics fast).

### Redis (key-value): forecasts and sessions

- City: `city:{regioncode}-citycode-lat-lon` (hash with region and elevation)
  e.g. `city:{tus}-pis-43.7085-10.4036`
- Forecast: `forecast:{regioncode}-citycode-lat-lon:YYYY-MM-DD` (string with temperature, rainfall, snowfall, windspeed, timestamp)
  e.g. `forecast:{tus}-pis-43.7085-10.4036:2025-06-08`

**Cross-DB consistency:** the only shared entity is `city`; its insertion is rolled back if either write (Mongo or Redis) fails.

## Code structure

Located in `src/main/java/it.unipi.lsmsd/`:

| Package | Role |
|---|---|
| `controller` | API endpoints (`UserController`, `CityController`, `FavoriteCitiesController`, `EweController`, `HourlyMeasurementController`, `AnalyticsController`, `DataManagerController`, `GlobalExceptionHandler`) |
| `service` | Business logic (`UserService`, `CityService`, `FavoriteService`, `ExtremeWeatherEventService`, `HourlyMeasurementService`, `RedisForecastService`, `AnalyticsService`, `AutomatingService`) |
| `DTO` | Data transfer objects |
| `model` | `User`, `City`, `ExtremeWeatherEvent`, `HourlyMeasurement` and enums (`Role`, `ExtremeWeatherEventCategory`, `MeasurementField`) |
| `repository` | `MongoRepository` interfaces |
| `utility` | Helper functions |

### Notable logic

- **Arbitrary-city forecast:** a weighted average of the forecasts of cities in the same region, with weights based on the inverse of an *effective distance* (Haversine distance combined with elevation difference, scaled by empirical factors `alpha` and `beta`).
- **EWE detection:** measurements in a time range are compared with city thresholds; events grow in strength and duration while values stay out of threshold. A separate method removes overlapping duplicates.
- **Daily automation:** `AutomatingService` refreshes measurements (previous day), forecasts (next week) and EWEs, meant to run when system load is lowest.
- **Transactions:** follow/unfollow are `@Transactional`, updating `listCityId` and `followers` atomically.

## API overview

| Area | Endpoints |
|---|---|
| **User** | `POST /user/register`, `POST /user/login`, `POST /user/logout` |
| **Forecasts** | `GET /forecast/today`, `/forecast/day`, `/forecast/week`, `/forecast/today/arbitrary-city`, `/forecast/day/arbitrary-city` |
| **City** (admin) | `POST /city/add`, `PUT /city/update-thresholds`, `GET /city/by-name`, `GET /city/all` |
| **Favorites** (registered) | `GET`, `PUT`, `DELETE /favorites` |
| **Data manager** (admin) | `PUT /data-manager/update/forecasts`, `/measurements`, `/ewes` |
| **EWE cleanup** (admin) | `DELETE /ewe/duplicates/range`, `/ewe/duplicates/all` |
| **Analytics: city** | `GET /analytics/measurement` and `/city/{lowest, highest, average, average-per-month}` |
| **Analytics: region** | `GET /analytics/measurement/region/{average, lowest, highest}` |
| **Analytics: recent** | `GET /analytics/measurement/recent/city/{total-per-day, average-per-day}`, `/recent/region/{total, average}` |
| **Analytics: EWE** | `GET /analytics/ewe/{strength/maximum, strength/average, duration/longest, duration/average, count, count-per-month, count-of-at-least-strength}` |

Analytics endpoints take a measurement field (`RAINFALL`, `SNOWFALL`, `WINDSPEED`, `TEMPERATURE`) or an EWE category, plus a time range (or `pastDays` for recent ones).

## Clustering and sharding

Deployed on 3 University of Pisa VMs (`10.1.1.9`, `10.1.1.84`, `10.1.1.87`). Since the application is read-heavy, a **master-replica** architecture is combined with sharding (each shard has a dedicated master and replica).

- **MongoDB:** replica set `rswa` with 3 members (priorities 5/3/1), write concern `w=1` and read preference `local`. Sharding is only a *proposed design*: `users` and `cities` by id, `measurements` by `cityId` (so per-city analytics stay on one node).
- **Redis Cluster:** one master and one replica per node (ports `6379` / `6380`), `appendonly yes`, `maxmemory 512mb` with `allkeys-lfu` eviction. Hash tags `{regioncode}` keep a region's cities and forecasts on the same slot/node.
- **CAP:** the system is designed as **AP**, favoring availability over strict consistency.

