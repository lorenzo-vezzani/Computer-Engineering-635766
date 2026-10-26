import logging
import threading
from datetime import datetime
from queue import Empty, Queue

import mysql.connector


logger = logging.getLogger(__name__)


class Database:
    """MySQL access layer."""

    def __init__(self, host, port, user, password, database):
        self.config = {
            "host": host,
            "port": port,
            "user": user,
            "password": password,
            "database": database,
        }
        self.connection = None

    def connect(self):
        if self.connection is None or not self.connection.is_connected():
            self.connection = mysql.connector.connect(**self.config)

    def close(self):
        if self.connection is not None and self.connection.is_connected():
            self.connection.close()

    def _cursor(self):
        self.connect()
        return self.connection.cursor(dictionary=True)

    # ============================================================
    # MEASUREMENTS
    # ============================================================

    def insert_measurement(
        self,
        room_id,
        timestamp,
        temperature=None,
        humidity=None,
        light=None,
        co2=None
    ):
        sql = """
            INSERT INTO measurement
                (room_id, timestamp, temperature, humidity, light, co2)
            VALUES
                (%s, %s, %s, %s, %s, %s)
        """

        cursor = self._cursor()

        try:
            cursor.execute(
                sql,
                (
                    room_id,
                    timestamp,
                    temperature,
                    humidity,
                    light,
                    co2
                ),
            )

            self.connection.commit()

        finally:
            cursor.close()

    def get_measurements(self, room_id=None, begin=None, end=None):
        conditions = []
        params = []

        if room_id is not None:
            conditions.append("room_id = %s")
            params.append(room_id)

        if begin is not None:
            conditions.append("timestamp >= %s")
            params.append(begin)

        if end is not None:
            conditions.append("timestamp <= %s")
            params.append(end)

        sql = """
            SELECT
                id,
                room_id,
                timestamp,
                temperature,
                humidity,
                light,
                co2
            FROM measurement
        """

        if conditions:
            sql += " WHERE " + " AND ".join(conditions)

        sql += " ORDER BY timestamp ASC"

        cursor = self._cursor()

        try:
            cursor.execute(sql, tuple(params))
            return cursor.fetchall()

        finally:
            cursor.close()


    def get_room_id_by_name(self, room_name):
        query = """
            SELECT id
            FROM room
            WHERE name = %s
        """

        with self.connection.cursor(dictionary=True) as cursor:
            cursor.execute(query, (room_name,))
            row = cursor.fetchone()

        if row is None:
            raise ValueError(f"Unknown room: '{room_name}'")

        return row["id"]


    def get_latest_measurements(self):
        """
        Return the latest measurement available for every room.
        """

        sql = """
            SELECT
                m.id,
                m.room_id,
                m.timestamp,
                m.temperature,
                m.humidity,
                m.light,
                m.co2
            FROM measurement m

            INNER JOIN (
                SELECT
                    room_id,
                    MAX(timestamp) AS latest_timestamp
                FROM measurement
                GROUP BY room_id
            ) latest

            ON m.room_id = latest.room_id
            AND m.timestamp = latest.latest_timestamp

            ORDER BY m.room_id
        """

        cursor = self._cursor()

        try:
            cursor.execute(sql)
            return cursor.fetchall()

        finally:
            cursor.close()

    # ============================================================
    # SYSTEM METRICS
    # ============================================================

    def save_system_metrics(
        self,
        room_id,
        timestamp,
        duration_seconds,
        energy_consumed_wh,
        packet_loss_percentage,
        fall_back_count,
        adaptive_mode=True,
        comfort_level=None,
    ):
        # NOTE: requires a "comfort_level" column on system_metrics, e.g.:
        #   ALTER TABLE system_metrics ADD COLUMN comfort_level FLOAT NULL;
        query = """
            INSERT INTO system_metrics (
                timestamp,
                room_id,
                adaptive_mode,
                duration_seconds,
                energy_consumed_wh,
                packet_loss,
                fall_back_count,
                comfort_level
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        """

        cursor = self._cursor()

        try:
            cursor.execute(
                query,
                (
                    timestamp,
                    room_id,
                    adaptive_mode,
                    duration_seconds,
                    energy_consumed_wh,
                    packet_loss_percentage,
                    fall_back_count,
                    comfort_level,
                ),
            )

            self.connection.commit()

        finally:
            cursor.close()

    # ============================================================
    # HVAC ACTIVATION
    # ============================================================

    def start_activation(self, room_id, start_time):
        sql = """
            INSERT INTO activation
                (room_id, start_time, end_time, energy_consumed_wh)
            VALUES
                (%s, %s, NULL, NULL)
        """

        cursor = self._cursor()

        try:
            cursor.execute(
                sql,
                (room_id, start_time)
            )

            self.connection.commit()

            return cursor.lastrowid

        finally:
            cursor.close()

    def end_activation(
        self,
        room_id,
        end_time,
        energy_consumed_wh
    ):
        sql = """
            UPDATE activation
            SET
                end_time = %s,
                energy_consumed_wh = %s
            WHERE id = (
                SELECT id
                FROM (
                    SELECT id
                    FROM activation
                    WHERE room_id = %s
                    AND end_time IS NULL
                    ORDER BY start_time DESC
                    LIMIT 1
                ) AS latest_activation
            )
        """

        cursor = self._cursor()

        try:
            cursor.execute(
                sql,
                (
                    end_time,
                    energy_consumed_wh,
                    room_id
                )
            )

            self.connection.commit()

        finally:
            cursor.close()

    # ============================================================
    # ACTIVATIONS
    # ============================================================

    def get_activations(
        self,
        room_id=None,
        begin=None,
        end=None
    ):
        conditions = []
        params = []

        if room_id is not None:
            conditions.append("room_id = %s")
            params.append(room_id)

        if begin is not None:
            conditions.append("start_time >= %s")
            params.append(begin)

        if end is not None:
            conditions.append("start_time <= %s")
            params.append(end)

        sql = """
            SELECT
                id,
                room_id,
                start_time,
                end_time,
                energy_consumed_wh
            FROM activation
        """

        if conditions:
            sql += " WHERE " + " AND ".join(conditions)

        sql += " ORDER BY start_time ASC"

        cursor = self._cursor()

        try:
            cursor.execute(sql, tuple(params))
            return cursor.fetchall()

        finally:
            cursor.close()


# =================================================================
# DATABASE REQUEST
# =================================================================

class DatabaseRequest:
    """
    Represents an operation requested by another component.

    Examples:

        DatabaseRequest(
            "get_measurements",
            {
                "room_id": 1
            }
        )

        DatabaseRequest(
            "start_activation",
            {
                "room_id": 1,
                "start_time": datetime.now()
            }
        )
    """

    def __init__(self, operation, params=None):
        self.operation = operation
        self.params = params or {}

        self.response = Queue(maxsize=1)


# =================================================================
# DATABASE THREAD
# =================================================================

class DatabaseThread(threading.Thread):
    """
    Dedicated database thread.

    Every database operation is executed by this thread.
    """

    def __init__(
        self,
        database,
        measurement_queue,
        request_queue,
        shutdown_event
    ):
        super().__init__(
            name="database-thread",
            daemon=True
        )

        self.database = database
        self.measurement_queue = measurement_queue
        self.request_queue = request_queue
        self.shutdown_event = shutdown_event

    # -------------------------------------------------------------
    # Execute request
    # -------------------------------------------------------------

    def _execute_request(self, request):

        try:

            if request.operation == "get_measurements":

                result = self.database.get_measurements(
                    **request.params
                )

            elif request.operation == "get_latest_measurements":

                result = self.database.get_latest_measurements()

            elif request.operation == "start_activation":

                result = self.database.start_activation(
                    **request.params
                )

            elif request.operation == "end_activation":

                result = self.database.end_activation(
                    **request.params
                )

            elif request.operation == "get_activations":

                result = self.database.get_activations(
                    **request.params
                )

            elif request.operation == "save_system_metrics":

                result = self.database.save_system_metrics(
                    **request.params
                )

            else:
                raise ValueError(
                    f"Unknown database operation: "
                    f"{request.operation}"
                )

            request.response.put(
                {
                    "success": True,
                    "result": result
                }
            )

        except Exception as exc:

            logger.exception(
                "Database operation '%s' failed",
                request.operation
            )

            request.response.put(
                {
                    "success": False,
                    "error": exc
                }
            )

    # -------------------------------------------------------------
    # Thread
    # -------------------------------------------------------------

    def run(self):

        try:

            self.database.connect()

            logger.info("Connected to MySQL")

            while (
                not self.shutdown_event.is_set()
                or not self.measurement_queue.empty()
                or not self.request_queue.empty()
            ):

                # -------------------------------------------------
                # Measurements have priority
                # -------------------------------------------------

                try:
                    measurement = (
                        self.measurement_queue.get_nowait()
                    )

                except Empty:
                    measurement = None

                if measurement is not None:
                    try:
                        self.database.insert_measurement(
                            **measurement
                        )

                    except Exception:
                        logger.exception(
                            "Unable to store measurement"
                        )

                    finally:
                        self.measurement_queue.task_done()

                    continue

                # -------------------------------------------------
                # Database requests
                # -------------------------------------------------

                try:
                    request = self.request_queue.get(
                        timeout=0.5
                    )

                except Empty:
                    continue

                try:
                    self._execute_request(request)

                finally:
                    self.request_queue.task_done()

        except Exception:
            logger.exception(
                "Database thread failed"
            )

        finally:
            self.database.close()

            logger.info(
                "Database thread stopped"
            )
