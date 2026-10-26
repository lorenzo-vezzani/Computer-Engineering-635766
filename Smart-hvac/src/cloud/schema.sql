DROP DATABASE IF EXISTS iot_cloud;

CREATE DATABASE IF NOT EXISTS iot_cloud;


USE iot_cloud;


CREATE TABLE IF NOT EXISTS room (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);


CREATE TABLE IF NOT EXISTS measurement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id INT NOT NULL,
    timestamp DATETIME NOT NULL,
    temperature DOUBLE NULL,
    humidity DOUBLE NULL,
    light DOUBLE NULL,
    co2 DOUBLE NULL,

    CONSTRAINT fk_measurement_room
        FOREIGN KEY (room_id) REFERENCES room(id),

    INDEX idx_measurement_room_timestamp (room_id, timestamp)
);


CREATE TABLE IF NOT EXISTS activation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NULL,
    energy_consumed_wh FLOAT NULL,

    CONSTRAINT fk_activation_room
        FOREIGN KEY (room_id) REFERENCES room(id),

    INDEX idx_activation_room_time (room_id, start_time)
);


INSERT INTO room (id, name)
VALUES
    (1, 'room1'),
    (2, 'room2'),
    (3, 'room3'),
    (4, 'room4'),
    (5, 'room5'),
    (6, 'room6'),
    (7, 'room7'),
    (8, 'room8'),
    (9, 'room9'),
    (10, 'room10')
ON DUPLICATE KEY UPDATE name = VALUES(name);


CREATE TABLE IF NOT EXISTS system_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp DATETIME(3) NOT NULL,
    room_id INT NOT NULL,
    adaptive_mode BOOLEAN NOT NULL DEFAULT TRUE,
    duration_seconds FLOAT DEFAULT 0,
    energy_consumed_wh FLOAT DEFAULT 0,
    packet_loss FLOAT DEFAULT 0,
    comfort_level FLOAT DEFAULT 0,
    fall_back_count INT DEFAULT 0,

    CONSTRAINT fk_system_metrics_room
        FOREIGN KEY (room_id) REFERENCES room(id)
);


/*
Grafana queries

--- ENERGY
SELECT
    mode,
    AVG(room_energy) AS energy_wh
FROM (
    SELECT
        room_id,
        adaptive_mode,
        CASE
            WHEN adaptive_mode = TRUE THEN 'Adaptive'
            ELSE 'Not Adaptive'
        END AS mode,
        AVG(energy_consumed_wh) AS room_energy
    FROM system_metrics
    WHERE packet_loss = ${packet_loss}
    GROUP BY room_id, adaptive_mode
) AS normalized
GROUP BY mode;



--- FALLBACK
SELECT sum(fall_back_count) as Total_fallbacks, packet_loss
FROM system_metrics
GROUP BY packet_loss



--- ENERGY SAVING
SELECT
    100.0 * (
        MAX(CASE WHEN mode = 'Not Adaptive' THEN energy_wh END)
        -
        MAX(CASE WHEN mode = 'Adaptive' THEN energy_wh END)
    )
    /
    MAX(CASE WHEN mode = 'Not Adaptive' THEN energy_wh END)
    AS energy_saving_percent
FROM (
    SELECT
        mode,
        AVG(room_energy) AS energy_wh
    FROM (
        SELECT
            room_id,
            CASE
                WHEN adaptive_mode = TRUE THEN 'Adaptive'
                ELSE 'Not Adaptive'
            END AS mode,
            AVG(energy_consumed_wh) AS room_energy
        FROM system_metrics
        WHERE packet_loss = ${packet_loss}
        GROUP BY room_id, adaptive_mode
    ) AS per_room
    GROUP BY mode
) AS totals;



--- COMFORT LEVEL
SELECT
    CASE
        WHEN adaptive_mode = 1 THEN 'Adaptive'
        ELSE 'Non-adaptive'
    END AS mode,
    ROUND(AVG(comfort_level) * 100) AS comfort_percentage
FROM iot_cloud.system_metrics
GROUP BY adaptive_mode;
*/