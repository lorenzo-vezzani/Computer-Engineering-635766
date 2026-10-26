#!/bin/sh

# Startup-config

echo "n2" > /etc/hostname
hostname n2

# === Interfaces ===
ip link set eth1 up
ip addr add 198.51.100.10/24 dev eth1


# === Static routing ===
# Add static entries
ip route replace 0.0.0.0/0 via 198.51.100.1


echo "Alpine: n2 is up and running"