import json
import logging
import threading
from queue import Queue, Full

import paho.mqtt.client as mqtt


logger = logging.getLogger(__name__)


class MQTTClient(threading.Thread):
    """
    MQTT receiver/publisher thread.

    Incoming sensor measurements are parsed and pushed into a queue.
    The same client can be used by the controller to publish HVAC commands.
    """

    def __init__(
        self,
        broker_host,
        broker_port,
        topics,
        _queue,
        shutdown_event,
        client_id="cloud-mqtt-client",
        username=None,
        password=None,
        keepalive=60,
        queue_timeout=0.1,
    ):
        super().__init__(name="mqtt-thread", daemon=True)

        self.broker_host = broker_host
        self.broker_port = broker_port
        self.topics = topics
        self._queue = _queue
        self.shutdown_event = shutdown_event
        self.keepalive = keepalive
        self.queue_timeout = queue_timeout

        self.client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=client_id,
        )

        if username is not None:
            self.client.username_pw_set(username, password)

        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.on_disconnect = self._on_disconnect

        self._connected = threading.Event()

    def _on_connect(self, client, userdata, flags, reason_code, properties):
        if reason_code.is_failure:
            logger.error("MQTT connection failed: %s", reason_code)
            return

        logger.info("Connected to MQTT broker %s:%s", self.broker_host, self.broker_port)

        for topic in self.topics:
            result, _ = client.subscribe(topic)
            if result != mqtt.MQTT_ERR_SUCCESS:
                logger.error("Unable to subscribe to topic '%s'", topic)
            else:
                logger.info("Subscribed to '%s'", topic)

        self._connected.set()

    def _on_disconnect(self, client, userdata, disconnect_flags, reason_code, properties):
        self._connected.clear()
        logger.warning("Disconnected from MQTT broker: %s", reason_code)

    def _on_message(self, client, userdata, message):
        try:
            payload = json.loads(message.payload.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            logger.error(
                "Invalid MQTT payload on '%s': %s",
                message.topic,
                exc,
            )
            return

        if not isinstance(payload, (dict, list)):
            # Regular commands (HVAC on/off/power, threshold, ...) are
            # JSON objects; sensor telemetry is now a SenML Pack
            # (RFC 8428), i.e. a JSON array of records.
            logger.error(
                "MQTT payload on '%s' is not a JSON object or array",
                message.topic,
            )
            return

        # The topic is kept in the measurement so that the application can
        # associate the message with the configured sensor/room.
        measurement = {
            "topic": message.topic,
            "payload": payload,
        }

        try:
            self._queue.put(measurement, timeout=self.queue_timeout)
        except Full:
            logger.error("Measurement queue is full; dropping MQTT message")

    def publish(self, topic, payload, qos=0, retain=False):
        """
        Publish a JSON object. Returns True if paho accepted the message.
        """
        if not self._connected.is_set():
            logger.warning("Cannot publish: MQTT client is not connected")
            return False

        if isinstance(payload, dict):
            payload = json.dumps(payload)

        result = self.client.publish(
            topic,
            payload=payload,
            qos=qos,
            retain=retain,
        )

        if result.rc != mqtt.MQTT_ERR_SUCCESS:
            logger.error("MQTT publish failed on '%s': %s", topic, result.rc)
            return False

        return True

    def is_connected(self):
        return self._connected.is_set()

    def stop(self):
        self.shutdown_event.set()

    def run(self):
        try:
            self.client.connect(
                self.broker_host,
                self.broker_port,
                self.keepalive,
            )
        except Exception:
            logger.exception("Unable to connect to MQTT broker")
            return

        self.client.loop_start()

        try:
            while not self.shutdown_event.is_set():
                self.shutdown_event.wait(0.5)
        finally:
            self.client.loop_stop()
            try:
                self.client.disconnect()
            except Exception:
                logger.exception("Error while disconnecting MQTT client")
            self._connected.clear()
            logger.info("MQTT thread stopped")
