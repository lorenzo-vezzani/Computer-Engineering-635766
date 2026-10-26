#!/bin/sh

# Startup-config

echo "MNGR" > /etc/hostname
hostname MNGR

# === Interfaces ===
ip link set eth1 up
ip addr add 192.168.0.5/29 dev eth1



# === Python file ===
# Nohup to let it run in background
# Redirect output to log file
# -u for unbuffered output
nohup python3 -u /main.py > /log_file.log 2>&1 &

# Messages
echo Python code is running in the background
echo Output is being redirected to ./log_file.log
echo Type tail -f log_file.log to see the live output

echo "Alpine: MNGR is up and running"