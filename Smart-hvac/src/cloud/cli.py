import argparse
import logging
import shlex
import threading
from datetime import datetime


logger = logging.getLogger(__name__)


class CLI(threading.Thread):
    """Interactive command-line interface."""

    def __init__(
        self,
        database,
        controller,
        shutdown_event,
        status_provider=None,
    ):
        super().__init__(name="cli-thread", daemon=False)

        self.database = database
        self.controller = controller
        self.shutdown_event = shutdown_event
        self.status_provider = status_provider or controller.get_status

    def _print_help(self):
        print(
            """
Available commands:

  q, quit
      Exit the application.

  h, help
      Show this help message.

  s, status
      Show the current status of every known device.

  d [-s ROOM_ID] [-p BEGIN END]
      Show stored sensor measurements.
      -s / --sensor selects a room/sensor.
      -p / --period selects a time interval.

  ct <ROOM_ID> <THRESHOLD>, change-threshold <ROOM_ID> <THRESHOLD>
      Change the HVAC temperature threshold.
      Example: ct 22.0

  t <ROOM_ID>, test <ROOM_ID> <MODE>
      Start a system_metrics test session for a room. 
      By default adaptive mode is selected, use 'na' for non adaptive mode.

  et <ROOM_ID>, end-test <ROOM_ID>
      End the test session for a room, compute the metrics
      (packet loss, average energy, fall-back count, duration)
      and store them in system_metrics.
"""
        )

    def _print_status(self):
        status = self.status_provider()

        if not status:
            print("No device status available.")
            return

        print("\nDEVICE STATUS")

        for room_id in sorted(status):
            state = status[room_id]

            print(f"\nRoom {room_id}")
            print(f"  Timestamp:   {state.get('timestamp', '-')}")
            print(f"  Temperature: {state.get('temperature', '-')}")
            print(f"  Humidity:    {state.get('humidity', '-')}")
            print(f"  Light:       {state.get('light', '-')}")
            print(f"  CO2:         {state.get('co2', '-')}")
            print(f"  HVAC:        {state.get('hvac_power', 'UNKNOWN')}")

            
    def _parse_datetime(self, value):
        try:
            return datetime.fromisoformat(value)
        except ValueError:
            raise ValueError(
                f"Invalid datetime '{value}'. "
                "Use ISO-8601, e.g. 2026-08-25T10:00:00"
            )

    def _show_measurements(self, args):
        begin = self._parse_datetime(args.period[0]) if args.period else None
        end = self._parse_datetime(args.period[1]) if args.period else None

        room_id = None

        if args.sensor is not None:
            try:
                room_id = self.database.get_room_id_by_name(args.sensor)
            except ValueError as exc:
                print(f"Error: {exc}")
                return

        rows = self.database.get_measurements(
            room_id=room_id,
            begin=begin,
            end=end,
        )

        if not rows:
            print("No measurements found.")
            return

        print(
            "\n"
            f"{'ROOM':<8}"
            f"{'TIMESTAMP':<22}"
            f"{'TEMP':<10}"
            f"{'HUM':<10}"
            f"{'LIGHT':<10}"
            f"{'CO2':<10}"
        )

        print("-" * 68)

        for row in rows:
            print(
                f"{row['room_id']:<8}"
                f"{str(row['timestamp']):<22}"
                f"{str(row['temperature']):<10}"
                f"{str(row['humidity']):<10}"
                f"{str(row['light']):<10}"
                f"{str(row['co2']):<10}"
            )

    def _handle_measurements(self, tokens):
        parser = argparse.ArgumentParser(
            prog="d",
            add_help=False,
        )

        parser.add_argument(
            "-s",
            "--sensor",
            type=str,
            default=None,
        )

        parser.add_argument(
            "-p",
            "--period",
            nargs=2,
            metavar=("BEGIN", "END"),
            default=None,
        )

        try:
            args = parser.parse_args(tokens)
            self._show_measurements(args)
        except (SystemExit, ValueError) as exc:
            if isinstance(exc, ValueError):
                print(f"Error: {exc}")

    def _change_threshold(self, tokens):

        if len(tokens) != 2:
            print(
                "Usage: ct <room_id> <threshold>"
            )
            return

        try:
            room_id = self._parse_room_id(tokens, "Usage: et <room_id>")
        except IndexError:
            print(
                f"Room_ID must be a integer" \
            )
            return

        try:
            threshold = float(tokens[1])
        except ValueError:
            print(
                f"Invalid threshold: '{tokens[1]}'. "
                "Threshold must be a number."
            )
            return

        try:
            self.controller.set_threshold(room_id, threshold)
        except ValueError as exc:
            print(f"Error: {exc}")
            return

        print(
            f"HVAC threshold changed to {threshold:.2f} °C"
        )


    def _parse_room_id(self, tokens, usage):
        if len(tokens) < 1:
            print(usage)
            return None

        room_name = tokens[0]

        try:
            return self.database.get_room_id_by_name(room_name)
        except ValueError as exc:
            print(f"Error: {exc}")
            return None


    def _start_test(self, tokens):
        room_id = self._parse_room_id(tokens, "Usage: t <room_id> <packet_loss_percentage>")

        if room_id is None:
            return

        try:
            packet_loss_percentage = int(tokens[1])
        except ValueError:
            print(
                f"Invalid plr: '{tokens[1]}'. "
            )
            return

        adaptive = True
        if(len(tokens) > 2):
            mode = tokens[2]

            if(mode != 'na'):
                print("Not recognized mode, select na for non-adaptive. By default adaptive is selected")
                return

            adaptive = False

        self.controller.start_test(room_id, packet_loss_percentage, adaptive)

        if adaptive:
            print(f"Adaptive test started on room {room_id}.")
        else:
            print(f"Not-adaptive test started on room {room_id}.")

    def _end_test(self, tokens):
        room_id = self._parse_room_id(tokens, "Usage: et <room_id>")

        if room_id is None:
            return

        metrics = self.controller.end_test(room_id)

        print(f"metrics: {metrics}")

        if metrics is None:
            print(f"No active test found for room {room_id}.")
            return

        try:
            self.database.save_system_metrics(**metrics)
        except Exception as exc:
            print(f"Error saving test metrics: {exc}")
            return

        print(
            f"\nTest completed for room {metrics['room_id']}\n"
            f"  Duration:     {metrics['duration_seconds']:.2f} s\n"
            f"  Energy:       {metrics['energy_consumed_wh']:.2f} Wh\n"
            f"  Packet loss %: {metrics['packet_loss_percentage'] * 100:.2f} %\n"
            f"  Fall-backs:   {metrics['fall_back_count']}"
        )

    def _handle_command(self, line):
        try:
            tokens = shlex.split(line)
        except ValueError as exc:
            print(f"Invalid command: {exc}")
            return

        if not tokens:
            return

        command = tokens[0].lower()
        arguments = tokens[1:]

        if command in ("q", "quit"):
            self.shutdown_event.set()
            return

        if command in ("h", "help"):
            self._print_help()
            return

        if command in ("s", "status"):
            self._print_status()
            return

        if command == "d":
            self._handle_measurements(arguments)
            return

        if command in ("ct", "change-threshold"):
            self._change_threshold(arguments)
            return

        if command in ("t", "test"):
            self._start_test(arguments)
            return

        if command in ("et", "end-test"):
            self._end_test(arguments)
            return

        print(
            f"Unknown command: '{command}'. "
            "Type 'h' for help."
        )

    def run(self):
        self._print_help()

        while not self.shutdown_event.is_set():
            try:
                line = input("> ")
            except EOFError:
                self.shutdown_event.set()
                break
            except KeyboardInterrupt:
                print()
                self.shutdown_event.set()
                break

            self._handle_command(line)

        logger.info("CLI thread stopped")