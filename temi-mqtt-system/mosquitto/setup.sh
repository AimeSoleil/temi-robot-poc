#!/bin/bash
# Run this to set up the broker

# 1. Create password file (username: temi, password: temi2026)
docker run --rm -v $(pwd):/data eclipse-mosquitto:2 \
  mosquitto_passwd -b -c /data/password.txt temi temi2026

# 2. Start the broker
echo "Starting MQTT broker..."
docker compose up -d

echo "MQTT broker is running on port 1884 (MQTT) and 9002 (WebSocket)"
