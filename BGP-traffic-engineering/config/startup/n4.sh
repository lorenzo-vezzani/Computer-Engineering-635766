#!/bin/sh

# Startup-config

echo "n4" > /etc/hostname
hostname n4

# === Interfaces ===
ip link set eth1 up
ip addr add 203.0.113.138/25 dev eth1


# === Static routing ===
# Add static entries
ip route replace 0.0.0.0/0 via 203.0.113.129


echo "Alpine: n4 is up and running"