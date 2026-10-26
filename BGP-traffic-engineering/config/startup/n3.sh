#!/bin/sh

# Startup-config

echo "n3" > /etc/hostname
hostname n3

# === Interfaces ===
ip link set eth1 up
ip addr add 203.0.113.10/25 dev eth1


# === Static routing ===
# Add static entries
ip route replace 0.0.0.0/0 via 203.0.113.1


echo "Alpine: n3 is up and running"