"""
Example application entry point.

The main thread is responsible only for application
initialization and thread management.
"""

import json
import logging
import queue
import threading

from mqtt_client import MQTTClient
from database import Database, DatabaseThread
from controller import Controller
from dispathcer import Dispatcher
from cli import CLI


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(threadName)s] %(levelname)s: %(message)s",
)


def load_config(path="config/sensors.json"):
    with open(path, "r", encoding="utf-8") as file:
        return json.load(file)


def main():

    # ------------------------------------------------------------
    # CONFIGURATION
    # ------------------------------------------------------------

    config = load_config()

    mqtt_config = config["mqtt"]
    database_config = config["database"]
    rooms = config["rooms"]
    building = config["building"]


    # ------------------------------------------------------------
    # SHUTDOWN
    # ------------------------------------------------------------

    shutdown_event = threading.Event()


    # ------------------------------------------------------------
    # QUEUES
    # ------------------------------------------------------------

    # MQTTClient -> Dispatcher
    mqtt_queue = queue.Queue()

    # Dispatcher -> DatabaseThread
    database_queue = queue.Queue()

    # CLI / Dispatcher -> DatabaseThread
    database_request_queue = queue.Queue()

    # Dispatcher -> Controller
    controller_queue = queue.Queue()


    # ------------------------------------------------------------
    # MQTT CLIENT
    # ------------------------------------------------------------

    mqtt_topics = [
        topic
        for room in rooms
        for topic in (
            room["sensor_topic"],
            room["start_activation_topic"],
            room["end_activation_topic"],
            room["test_measurements_topic"],
        )
    ]

    mqtt_client = MQTTClient(
        broker_host=mqtt_config["host"],
        broker_port=mqtt_config.get("port", 1883),
        topics=mqtt_topics,
        _queue=mqtt_queue,
        shutdown_event=shutdown_event,
        client_id=mqtt_config.get(
            "client_id",
            "cloud-mqtt-client",
        ),
        username=mqtt_config.get("username"),
        password=mqtt_config.get("password"),
    )


    # ------------------------------------------------------------
    # DATABASE
    # ------------------------------------------------------------

    database = Database(
        host=database_config["host"],
        port=database_config.get("port", 3306),
        user=database_config["user"],
        password=database_config["password"],
        database=database_config["name"],
    )

    database_thread = DatabaseThread(
        database=database,
        measurement_queue=database_queue,
        request_queue=database_request_queue,
        shutdown_event=shutdown_event,
    )


    # ------------------------------------------------------------
    # CONTROLLER
    # ------------------------------------------------------------

    controller = Controller(
        mqtt_client=mqtt_client,
        controller_queue=controller_queue,
        shutdown_event=shutdown_event,
        rooms_config=rooms,
        max_power=building["max_power"],
    )


    # ------------------------------------------------------------
    # DISPATCHER
    # ------------------------------------------------------------

    dispatcher = Dispatcher(
        mqtt_queue=mqtt_queue,
        database_queue=database_queue,
        database_request_queue=database_request_queue,
        controller_queue=controller_queue,
        rooms_config=rooms,
        shutdown_event=shutdown_event,
    )


    # ------------------------------------------------------------
    # CLI
    # ------------------------------------------------------------

    cli = CLI(
        database=database,
        controller=controller,
        shutdown_event=shutdown_event,
    )


    # ------------------------------------------------------------
    # START THREADS
    # ------------------------------------------------------------

    mqtt_client.start()
    database_thread.start()
    controller.start()
    dispatcher.start()
    cli.start()


    # ------------------------------------------------------------
    # MAIN THREAD
    # ------------------------------------------------------------

    try:

        while not shutdown_event.is_set():
            shutdown_event.wait(1.0)

    except KeyboardInterrupt:

        logging.info(
            "Shutdown requested"
        )

        shutdown_event.set()

    finally:

        shutdown_event.set()


        # Wait for all application threads.
        dispatcher.join(timeout=5)
        cli.join(timeout=2)
        mqtt_client.join(timeout=5)
        controller.join(timeout=5)
        database_thread.join(timeout=5)


        logging.info(
            "Cloud application stopped"
        )


if __name__ == "__main__":
    main()