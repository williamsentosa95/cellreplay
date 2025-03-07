#!/bin/bash

# Define variables
SERVER_IP_ADDRESS="128.174.246.135"
SERVER_TRACE_FOLDER_PATH="/home/william/cellreplay-server-trace/"

SATURATOR_PORT="9000"
PACKET_TRAIN_PORT="9001"
PACKET_TRAIN_ECHO_DYNAMIC_PORT="9002"

# Execute the Python script with the specified variables
python3 ../server/saturator_server.py $SERVER_IP_ADDRESS $SATURATOR_PORT $SERVER_TRACE_FOLDER_PATH &
python3 ../server/packet_train_server.py $SERVER_IP_ADDRESS $PACKET_TRAIN_PORT $SERVER_TRACE_FOLDER_PATH &
python3 ../server/packet_train_echo_dynamic_server.py $SERVER_IP_ADDRESS $PACKET_TRAIN_ECHO_DYNAMIC_PORT $SERVER_TRACE_FOLDER_PATH
