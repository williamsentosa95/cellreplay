#!/usr/bin/env python
# -*- mode: python; coding: utf-8; fill-column: 80; -*-
#

# Description: script for doing calibration for the upload (U) or download (D) packet train size.

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

SATURATOR_CLASS_FILE = "../java-client/" + "SaturatorClient.jar"
PACKET_TRAIN_ECHO_DYNAMIC_JAR_FILE = "../java-client/" + "PacketTrainEchoDynamicClient.jar"
SATURATOR_DURATION_SECONDS = "10"

X = 0.5 # control the number of max tested set of train sizes
TRAIN_GAP_MS = 100

##### Fill this with your setup

SERVER_IP = "128.174.246.135"
# SERVER_IP = "127.0.0.1"
SSH_USERNAME = "william"
SERVER_SSH = SSH_USERNAME + "@" + SERVER_IP
HOME_PATH = "/home/william"
HOME_PATH_SERVER = "/home/william"
KEY = HOME_PATH + "/oceanvm.pem" # SSH Key for transfering traces from your remote server 

HOST_TRACE_FOLDER = "/home/william/cellreplay-test"
SERVER_TRACE_FOLDER = "/home/william/cellreplay-server-trace"

PYTHON3 = "python3.8"
JAVA = "java"

CLIENT_TEST_IP = "10.251.174.24"
CLIENT_TEST_DEV = "cscotun0"

SERVER_PORT_SATURATOR = 9000
SERVER_PORT_PACKET_TRAIN_ECHO_DYNAMIC = 9002

TEST_DIRECTION = 0 # 1 for Uplink (U) and 0 for Downlink (D)

##############################################################

# Global variable
current_process = None
current_process2 = None
current_log_process = None


def run_saturator(up_mbps, down_mbps, exp_condition, exp_id, exp_name):
    global current_process
    global current_log_process
    packet_size = 1400
    duration = int(SATURATOR_DURATION_SECONDS) + 5
    client_command = ["timeout", str(duration), JAVA, "-jar", SATURATOR_CLASS_FILE, HOST_TRACE_FOLDER, SERVER_IP, str(SERVER_PORT_SATURATOR), CLIENT_TEST_IP, str(SATURATOR_DURATION_SECONDS), str(packet_size), str(up_mbps), str(down_mbps), str(exp_id), str(exp_name), str(exp_condition)]
    print(client_command)
    current_process = subprocess.Popen(client_command)
    current_process.wait()
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
    global current_log_process
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

######################### Process the Saturator trace ##################################

def get_bandwidth(fpath):
    throughputs = []
    times = []
    trace = []
    with open(fpath, "r") as f:
        lines = f.readlines()

        # Var to calculate bandwidth
        start_time = -1
        time = 0
        times = []

        start_recv_time = 0
        curr_traffic = 0
        start_time = 0

        start_trace_recv_time = 0

        for line in lines:
            line = line.strip()
            if ("=" in line):
                # Process the config
                fields = line.split('=')
            else:
                fields = line.split();
                if (fields[0].isdigit() and len(fields) > 3):
                    # Process data
                    seq_num = int(fields[0])
                    send_timestamp = int(fields[1])
                    recv_timestamp = int(fields[2])
                    packet_size = int(fields[3])

                    if (start_recv_time <= 0):
                        start_recv_time = recv_timestamp

                    relative_recv_time = (int) ((recv_timestamp - start_recv_time) / 1000000)
                    
                    if (start_trace_recv_time <= 0):
                        start_trace_recv_time = recv_timestamp
                    
                    curr_traffic += packet_size
                    relative_recv_time = (int) ((recv_timestamp - start_trace_recv_time) / 1000000)
                    trace.append(relative_recv_time)

                    if (start_time <= 0):
                      start_time = recv_timestamp;
                    else:
                      spend_time_ms = (recv_timestamp - start_time) / 1e6
                      if (spend_time_ms >= 1000):
                          throughput = curr_traffic / packet_size
                          throughputs.append(throughput)
                          times.append(time)
                          time = time + 1
                          curr_traffic = 0
                          start_time = recv_timestamp

    return trace, times, throughputs

def contains_the_right_saturator(fpath, up_mbps, down_mbps):
    result = False
    with open(fpath, "r") as f:
        line = f.readlines()[0]
        upload_rate = line.split("upload_rate=")[1]
        if ("," in upload_rate):
            upload_rate = upload_rate.split(",")[0]
        else:
            upload_rate = upload_rate.split(" ")[0]
        download_rate = line.split("download_rate=")[1]
        if ("," in download_rate):
            download_rate = download_rate.split(",")[0]
        else:
            download_rate = download_rate.split(" ")[0]
        if (int(upload_rate) == up_mbps and int(download_rate) == down_mbps):
            result = True
    return result

def get_saturator_throughput(client_folder, server_folder, exp_name, up_mbps, down_mbps):
    saturator_client_folder = client_folder + "/" + exp_name
    saturator_server_folder = server_folder + "/saturator_trace"

    up_thps = []
    down_thps = []

    print(saturator_client_folder)
    assert(os.path.exists(saturator_server_folder) and os.path.exists(saturator_client_folder))

    # process download
    downloads = []
    for saturator_file in os.listdir(saturator_client_folder):
        if ("saturator" in saturator_file):
            fpath = os.path.join(saturator_client_folder, saturator_file)
            if (contains_the_right_saturator(fpath, up_mbps, down_mbps)):
                trace, times, thps = get_bandwidth(fpath)
                down_thps = down_thps + thps

    # process upload
    uploads = []
    for saturator_file in os.listdir(saturator_server_folder):
        if ("saturator_trace" in saturator_file):
            fpath = os.path.join(saturator_server_folder, saturator_file)
            if (contains_the_right_saturator(fpath, up_mbps, down_mbps)):
                trace, times, thps = get_bandwidth(fpath)
                up_thps = up_thps + thps

    up = int(np.mean(up_thps))
    down = int(np.mean(down_thps))
    return up, down

################################################################################################

def create_train_sizes(max_pps):
    train_sizes = [5, 10]

    curr_pps = 25
    while (curr_pps < max_pps):
        train_sizes.append(curr_pps)
        curr_pps += 25
    
    return train_sizes

def main():
    # ******* CONFIGURATIONS ********

    # Need to be changed
    exp_condition = "calibration-packet-train"
    exp_id = random.randint(0, 100000)

    # Our saturator configs
    saturator_up_mbps = 15
    saturator_down_mbps = 60
    
    ssh = ["ssh", "-i", KEY, SERVER_SSH]

    now = datetime.now()
    current_date = now.strftime("%m-%d-%Y")
    current_time = now.strftime("%H-%M-%S")

    # Saturator
    print("# Running Saturator")
    multiply_ratio = 1.25
    up_mbps = int(saturator_up_mbps * multiply_ratio)
    down_mbps = int(saturator_down_mbps * multiply_ratio)
    exp_name = "Saturator"
    run_saturator(up_mbps, down_mbps, exp_condition, exp_id, exp_name)
    time.sleep(5)
    client_folder, server_folder = organize_file(ssh, exp_condition, exp_id, current_date, current_time)


    up_packet_per_second, down_packet_per_second = get_saturator_throughput(client_folder, server_folder, exp_name, up_mbps, down_mbps)
    up_max_train_size = int(up_packet_per_second * (TRAIN_GAP_MS / 1000) * X)
    down_max_train_size = int(down_packet_per_second * (TRAIN_GAP_MS / 1000) * X)
    up_bw_mbps = up_packet_per_second * 1400 * 8 / 1e6
    down_bw_mbps = down_packet_per_second * 1400 * 8 / 1e6

    # packet train echo dynamic configs
    packet_train_echo_dynamic_packet_size = 1400
    packet_train_echo_dynamic_gap_ms = [TRAIN_GAP_MS]
    packet_train_echo_dynamic_up_num = create_train_sizes(up_max_train_size)
    packet_train_echo_dynamic_down_num = create_train_sizes(down_max_train_size)
    only_echo_first_and_last = 1

    if (TEST_DIRECTION == 0):
        packet_train_echo_dynamic_exp_duration = len(packet_train_echo_dynamic_gap_ms) * len(packet_train_echo_dynamic_down_num) * 5 * 3
    else:
        packet_train_echo_dynamic_exp_duration = len(packet_train_echo_dynamic_gap_ms) * len(packet_train_echo_dynamic_up_num) * 5 * 3

    print("**********************")
    print("Link can support %d pps (@1400 bytes) uplink and %d pps (@1400 bytes) downlink" % (up_max_train_size, down_max_train_size))
    print("Suggested UP packet train set = %s" % (packet_train_echo_dynamic_up_num))
    print("Suggested DOWN packet train set = %s" % (packet_train_echo_dynamic_down_num))
    print("Your TEST_DIRECTION value is %d, so we only perform experiment to determine %s" % (TEST_DIRECTION, ("U (UPLINK TRAIN SIZE)" if TEST_DIRECTION == 1 else "D (DOWNLINK TRAIN SIZE)")))
    print("Test duration is approx %d sec" % (packet_train_echo_dynamic_exp_duration))
    print("**********************")
    
    time.sleep(3)
    print("Now running packet train sampling for %s..." % ("UPLINK" if TEST_DIRECTION == 1 else "DOWNLINK"))
    # Running Packet Train Echo
    random.shuffle(packet_train_echo_dynamic_gap_ms)
    random.shuffle(packet_train_echo_dynamic_up_num)
    random.shuffle(packet_train_echo_dynamic_down_num)

    # Run packet train experiment
    exp_name = "PacketTrainEchoDynamic"
    packet_size = packet_train_echo_dynamic_packet_size
    
    if (TEST_DIRECTION == 1):
        num_packet = packet_train_echo_dynamic_up_num
    else:
        num_packet = packet_train_echo_dynamic_down_num

    only_echo_first_and_last = 1
    run_packet_train_echo_dynamic(packet_size, packet_train_echo_dynamic_gap_ms, TEST_DIRECTION, num_packet, only_echo_first_and_last, exp_condition, exp_id, exp_name, packet_train_echo_dynamic_exp_duration)
    time.sleep(3)

    print("Moving experiment files to folders..")
    client_folder, server_folder = organize_file(ssh, exp_condition, exp_id, current_date, current_time)
    
    ######################## Process the packet train experiment trace ###########################
    if (TEST_DIRECTION == 1):
        print("To process the calibration data and get U, run this command:")
        print("python3 find_train_size.py %s %.2f" % (server_folder, up_bw_mbps))
    else:
        print("To process the calibration data and get D, run this command:")
        print("python3 find_train_size.py %s %.2f" % (client_folder, down_bw_mbps))


        
if __name__ == '__main__':
    main()
