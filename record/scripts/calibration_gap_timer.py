#!/usr/bin/env python
# -*- mode: python; coding: utf-8; fill-column: 80; -*-
#

# Description: script for doing calibration for G_min gap and F timer.

import subprocess
import os
import numpy as np
import random
from time import sleep
import time
import sys

from datetime import datetime
import shutil
import signal

##################### CONFIGURATIONS #############################
# Need to be filled before running the calibration

PACKET_TRAIN_ECHO_DYNAMIC_JAR_FILE = "../java-client/" + "PacketTrainEchoDynamicClient.jar"
NUM_TRIAL_FOR_EACH_PACKET_TRAIN_DYNAMIC_SAMPLE = 3

##### Fill this with your setup

SERVER_IP = "128.174.246.135"
# SERVER_IP = "127.0.0.1"
SSH_USERNAME = "william"
SERVER_SSH = SSH_USERNAME + "@" + SERVER_IP
KEY = "/home/william/oceanvm.pem" # SSH Key for transfering traces from your remote server 

HOST_TRACE_FOLDER = "/home/william/cellreplay-test"
SERVER_TRACE_FOLDER = "/home/william/cellreplay-server-trace"

PYTHON3 = "python3.8"
JAVA = "java"

CLIENT_TEST_IP = "10.251.134.12"
CLIENT_TEST_DEV = "cscotun0"

SERVER_PORT_PACKET_TRAIN_ECHO_DYNAMIC = 9002

TEST_DIRECTION = 1 # 1 for Uplink (U) and 0 for Downlink (D)
CALIBRATED_TRAIN_SIZE = 200 # Put the suggested train size based on the find_train_size.py

##############################################################

# Global variable
current_process = None


def array_to_string(arr):
    result = ""
    for i in range(0, len(arr) - 1):
        result = result + str(arr[i]) + ","
    if (len(arr) > 0):
        result = result + str(arr[len(arr) - 1])
    return result

def run_packet_train_echo_dynamic(packet_size, train_gap_ms, upload, num_packets, only_echo_first_and_last, exp_condition, exp_id, exp_name, duration):
    global current_process
    client_command = [JAVA, "-jar", PACKET_TRAIN_ECHO_DYNAMIC_JAR_FILE, HOST_TRACE_FOLDER, SERVER_IP, str(SERVER_PORT_PACKET_TRAIN_ECHO_DYNAMIC), CLIENT_TEST_IP, str(upload), str(duration), str(packet_size), array_to_string(train_gap_ms), array_to_string(num_packets), str(exp_id), str(exp_name), str(exp_condition)]
    print(client_command)
    current_process = subprocess.Popen(client_command)
    current_process.wait()
    current_process = None

def organize_file(ssh, exp_cond, exp_id, date, time):
    # Create one folder
    result_folder_path = HOST_TRACE_FOLDER + "/exp-result/"
    if (not os.path.exists(result_folder_path)):
        os.mkdir(result_folder_path)

    exp_condition_id = exp_cond + "_" + str(exp_id)
    
    result_folder_path = result_folder_path + "/" + date
    if (not os.path.exists(result_folder_path)):
        os.mkdir(result_folder_path)

    result_folder_path = result_folder_path + "/" + time + "+" + exp_condition_id
    if (not os.path.exists(result_folder_path)):
        os.mkdir(result_folder_path)    

    client_folder = result_folder_path + "/client/"
    server_folder = result_folder_path + "/server/"

    if (not os.path.exists(server_folder)):
        os.mkdir(server_folder)

    if (not os.path.exists(client_folder)):
        os.mkdir(client_folder)

    # Move exp results to client folder
    trace_base_folder = HOST_TRACE_FOLDER
    trace_folder = trace_base_folder + "/" + date + "/" + exp_condition_id

    if (os.path.exists(trace_folder)):
        print("Moving measurement traces to " + client_folder)
        dest = client_folder
        src = trace_folder + "/*"
        cmd = 'cp -r ' + src + ' ' + dest + '.'
        subprocess.call(cmd, shell=True)

    if (not os.path.exists(client_folder)):
        os.mkdir(client_folder)

    # Move trace result
    print("Moving files from server...")
    server_trace_path = SERVER_TRACE_FOLDER + "/" + date + "/" + str(exp_id) + "/*"
    cmd = ["scp", "-r", "-i", KEY, SERVER_SSH + ":" + server_trace_path, server_folder]
    subprocess.call(cmd)

    return client_folder, server_folder

def main():
    # ******* CONFIGURATIONS ********

    # Need to be changed
    exp_condition = "calibration-timer-gap"
    exp_id = random.randint(0, 100000)
    
    ssh = ["ssh", "-i", KEY, SERVER_SSH]

    now = datetime.now()
    current_date = now.strftime("%m-%d-%Y")
    current_time = now.strftime("%H-%M-%S")

    # packet train echo dynamic configs
    packet_train_echo_dynamic_packet_size = 1400
    packet_train_echo_dynamic_gap_ms = [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
    packet_train_echo_dynamic_num = [CALIBRATED_TRAIN_SIZE]
    only_echo_first_and_last = 1
    packet_train_echo_dynamic_exp_duration = len(packet_train_echo_dynamic_gap_ms) * 6 * NUM_TRIAL_FOR_EACH_PACKET_TRAIN_DYNAMIC_SAMPLE

    print("**********************")
    print("Tested packet train gap set = %s" % (packet_train_echo_dynamic_gap_ms))
    print("Your TEST_DIRECTION value is %d, so we only perform experiment to determine %s" % (TEST_DIRECTION, ("G_min and F for UPLINK" if TEST_DIRECTION == 1 else "G_min and F for DOWNLINK")))
    print("Test duration is approx %d sec" % (packet_train_echo_dynamic_exp_duration))
    print("**********************")
    
    time.sleep(3)
    print("Now running packet train sampling for %s..." % ("UPLINK" if TEST_DIRECTION == 1 else "DOWNLINK"))
    # Running Packet Train Echo
    random.shuffle(packet_train_echo_dynamic_gap_ms)
    random.shuffle(packet_train_echo_dynamic_num)

    # Run packet train experiment
    exp_name = "PacketTrainEchoDynamic"
    packet_size = packet_train_echo_dynamic_packet_size
    num_packet = packet_train_echo_dynamic_num

    only_echo_first_and_last = 1
    run_packet_train_echo_dynamic(packet_size, packet_train_echo_dynamic_gap_ms, TEST_DIRECTION, num_packet, only_echo_first_and_last, exp_condition, exp_id, exp_name, packet_train_echo_dynamic_exp_duration)
    time.sleep(3)

    print("Moving experiment files to folders..")
    client_folder, server_folder = organize_file(ssh, exp_condition, exp_id, current_date, current_time)
    
    ######################## Process the packet train experiment trace ###########################
    if (TEST_DIRECTION == 1):
        suggested_folder = server_folder + "/" + "server_dynamic_packet_train_trace"
        print("To process the calibration data and get UP G_min and F, run this command:")
        print("python3 find_gmin_and_f.py %s" % (suggested_folder))
    else:
        suggested_folder = client_folder + "/" + exp_name
        print("To process the calibration data and get DOWN G_min and F, run this command:")
        print("python3 find_gmin_and_f.py %s" % (suggested_folder))


        
if __name__ == '__main__':
    main()
