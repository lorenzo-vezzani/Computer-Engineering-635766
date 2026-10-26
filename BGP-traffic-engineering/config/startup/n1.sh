#!/bin/sh

# Startup-config

echo "n1" > /etc/hostname
hostname n1

# === Interfaces ===
ip link set eth1 up
ip addr add 192.0.2.10/24 dev eth1


# === Static routing ===
# Add static entries
ip route replace 0.0.0.0/0 via 192.0.2.1


echo "Alpine: n1 is up and running"