import logging
import queue
import threading
from datetime import datetime

from database import DatabaseRequest


logger = logging.getLogger(__name__)


def parse_senml(payload):
    """
    Resolve a SenML Pack (RFC 8428) JSON array into a flat
    {name: value} dict.

    Handles "bn"/"bt" (base name/time) resolution as per the spec,
    even though sensor.c currently omits them (both default to "").
    Only the "v" (numeric), "vs" (string) and "vb" (boolean) value
    fields are supported, since that's all sensor.c ever emits.
    """

    if not isinstance(payload, list) or not payload:
        raise ValueError(
            "Invalid SenML payload: expected a non-empty JSON array"
        )

    base_name = ""
    values = {}

    for record in payload:

        if not isinstance(record, dict):
            raise ValueError(
                "Invalid SenML record: expected a JSON object"
            )

        if "bn" in record:
            base_name = record["bn"]

        name = base_name + record.get("n", "")

        if "v" in record:
            value = record["v"]
        elif "vs" in record:
            value = record["vs"]
        elif "vb" in record:
            value = record["vb"]
        else:
            value = None

        values[name] = value

    return values


class Dispatcher(threading.Thread):
    """
    Dispatcher thread.

    Receives raw MQTT messages and routes normalized messages
    to the appropriate application components.

    Responsibilities:
        - consume messages from mqtt_queue
        - normalize MQTT payloads
        - forward measurements to Controller and DatabaseThread
        - forward HVAC activation events to DatabaseThread
    """

    def __init__(
        self,
        mqtt_queue,
        database_queue,
        database_request_queue,
        controller_queue,
        rooms_config,
        shutdown_event,
    ):
        super().__init__(name="Dispatcher")

        self.mqtt_queue = mqtt_queue
        self.database_queue = database_queue
        self.database_request_queue = database_request_queue
        self.controller_queue = controller_queue
        self.rooms_config = rooms_config
        self.shutdown_event = shutdown_event

    def normalize_message(self, message):
        """
        Convert a raw MQTT message into an application-level message.
        """

        topic = message["topic"]
        payload = message["payload"]

        for room in self.rooms_config:

            if topic == room["sensor_topic"]:
                values = parse_senml(payload)

                return {
                    "type": "measurement",
                    "room_id": room["id"],
                    # The SenML pack carries no wall-clock time (the
                    # sensor has no RTC), so messages are timestamped
                    # on arrival instead.
                    "timestamp": datetime.now(),
                    "temperature": values.get("temperature"),
                    "humidity": values.get("humidity"),
                    "light": values.get("light"),
                    "co2": values.get("co2"),
                    # Used by Controller to reconstruct packet loss
                    # during a CLI test session.
                    "sequence": values.get("sequence"),
                }

            if topic == room["test_measurements_topic"]:
                values = parse_senml(payload)

                return {
                    # Always published by the sensor, even when the
                    # "real" measurement above is dropped by the
                    # simulated packet loss. Used exclusively to feed
                    # comfort_level during a CLI test session; never
                    # written to the database and never used to
                    # update the live device state.
                    "type": "test_measurement",
                    "room_id": room["id"],
                    "timestamp": datetime.now(),
                    "temperature": values.get("temperature"),
                    "humidity": values.get("humidity"),
                    "light": values.get("light"),
                    "co2": values.get("co2"),
                    "sequence": values.get("sequence"),
                }

            if topic == room["start_activation_topic"]:
                return {
                    "type": "start_activation",
                    "room_id": room["id"],
                    "timestamp": payload.get(
                        "timestamp",
                        datetime.now()
                    ),
                }

            if topic == room["end_activation_topic"]:
                return {
                    "type": "end_activation",
                    "room_id": room["id"],
                    "timestamp": payload.get(
                        "timestamp",
                        datetime.now()
                    ),
                    "energy_consumed_wh": payload.get(
                        "energy_consumed_wh"
                    ),
                    "reason": payload.get("reason"),
                }

        raise ValueError(
            f"Unknown MQTT topic: {topic}"
        )

    def save_test_metrics(self, metrics):
        """
        Persist the metrics collected during a CLI test session
        ("t" / "et" commands), including comfort_level.

        `metrics` is the dict returned by Controller.end_test() (built
        by SystemMetrics.finalize()) and is ready to be unpacked as
        Database.save_system_metrics(**metrics).

        Routed through database_request_queue rather than calling the
        Database object directly, so the write happens on the
        dedicated DatabaseThread instead of the CLI/caller thread.
        """

        self.database_request_queue.put(
            DatabaseRequest(
                "save_system_metrics",
                metrics,
            )
        )

    def dispatch(self, message):
        """
        Route a normalized message to the appropriate queues.
        """

        message_type = message["type"]

        # Controller receives all normalized messages.
        self.controller_queue.put(message)

        # Measurements are stored in the database.
        #
        # NOTE: only the fields accepted by
        # Database.insert_measurement() are forwarded here ("type"
        # and "sequence" are Dispatcher/Controller-only fields and
        # must not reach the DB layer).
        if message_type == "measurement":

            self.database_queue.put(
                {
                    "room_id": message["room_id"],
                    "timestamp": message["timestamp"],
                    "temperature": message["temperature"],
                    "humidity": message["humidity"],
                    "light": message["light"],
                    "co2": message["co2"],
                }
            )

        # Always-on test-measurement side channel: already forwarded
        # to the controller above (for comfort_level tracking during
        # a CLI test session); it must never reach the database.
        elif message_type == "test_measurement":

            pass

        # HVAC activation start.
        elif message_type == "start_activation":

            self.database_request_queue.put(
                DatabaseRequest(
                    "start_activation",
                    {
                        "room_id": message["room_id"],
                        "start_time": message["timestamp"],
                    },
                )
            )

        # HVAC activation end.
        elif message_type == "end_activation":

            self.database_request_queue.put(
                DatabaseRequest(
                    "end_activation",
                    {
                        "room_id": message["room_id"],
                        "end_time": message["timestamp"],
                        "energy_consumed_wh": (
                            message["energy_consumed_wh"]
                        ),
                    },
                )
            )

        else:
            raise ValueError(
                f"Unknown message type: {message_type}"
            )

    def run(self):
        logger.info("Dispatcher started")

        while not self.shutdown_event.is_set():

            try:
                raw_message = self.mqtt_queue.get(
                    timeout=0.5
                )

            except queue.Empty:
                continue

            try:
                message = self.normalize_message(
                    raw_message
                )

                logger.debug(
                    "Normalized MQTT message: %s",
                    message,
                )

                self.dispatch(message)

            except Exception:
                logger.exception(
                    "Unable to process MQTT message"
                )

            finally:
                self.mqtt_queue.task_done()

        logger.info("Dispatcher stopped")