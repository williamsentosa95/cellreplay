#!/usr/bin/env python
# -*- mode: python; coding: utf-8; fill-column: 80; -*-
#

# Description: scripts to do a batched experiments to compare methods of getting an achievable throuhput trace

import os
import numpy as np

import sys
import math
# import matplotlib.pyplot as plt

SMALL_SIZE = 8
MEDIUM_SIZE = 15
BIGGER_SIZE = 20

def process_server_train_file(fpath):
    file = open(fpath)
    line_num = 0
    sender_id = None
    server_ip = None
    packet_size = None
    gap_ms = None
    up_num_packets = None
    down_num_packets = None
    upload = None
    
    relative_packet_arrivals = dict()
    first_packets_arrivals = dict()
    last_packets_arrivals = dict()
    
    IGNORE_FIRST_TRAINS_MS = 200

    current_key = None
    current_count = 0
    ignore_first_trains = 5

    # print(fpath)
    for line in file.readlines():
        if (line_num == 0):
            # Process header data
            line = line.split(" : ")[1]
            splits = line.split(" ")
            sender_id = splits[0].split("=")[1]
        else:
            # Process download/upload
            splits = line.split("\t")
            if (splits[0].isdigit()):
                train_id = int(splits[0])
                packet_id = int(splits[1])
                sent_time = int(splits[2])
                recv_time = int(splits[3])
                sender_id = int(splits[4])
                packet_size = int(splits[5])
                rtt = float(splits[6])
                train_size = int(splits[7])
                train_gap = int(splits[8])
                # key = str(train_gap) + "-" + str(train_size)
                key = train_gap

                if (key != current_key):
                    current_count = 0
                    current_key = key
                    ignore_first_trains = int(IGNORE_FIRST_TRAINS_MS / train_gap)

                if (key not in relative_packet_arrivals):
                    relative_packet_arrivals[key] = dict()

                if (current_count < ignore_first_trains):
                    if (packet_id == 0):
                        current_count += 1
                else:
                    key2 = str(sender_id) + "-" + str(train_id)
                    if (key2 not in first_packets_arrivals):
                        first_packets_arrivals[key2] = recv_time
                        relative_packet_arrivals[key][key2] = []
                    if (packet_id == 199):
                        last_packets_arrivals[key2] = recv_time
                    if (packet_id == 0):
                        key_temp = str(sender_id) + "-" + str(train_id - 1)
                        if (key_temp in last_packets_arrivals):
                            last_to_first_gap = (recv_time - last_packets_arrivals[key_temp]) / 1e6
                            # print("id=%d, last-to-first-gap=%d" % (train_id, last_to_first_gap))
                    relative_arrival_time = (recv_time - first_packets_arrivals[key2]) / 1e6
                    relative_packet_arrivals[key][key2].append(relative_arrival_time)
        line_num += 1

    file.close()
    return sender_id, relative_packet_arrivals

def process_dynamic_train_file(fpath):
    file = open(fpath)
    line_num = 0
    sender_id = None
    server_ip = None
    packet_size = None
    gap_ms = None
    up_num_packets = None
    down_num_packets = None
    
    relative_packet_arrivals = dict()
    first_packets_arrivals = dict()
    
    current_key = None
    current_count = 0
    ignore_first_trains = 0

    IGNORE_FIRST_TRAINS_MS = 200

    for line in file.readlines():
        if (line_num == 0):
            # Process header data
            splits = line.split(",")
            sender_id = splits[0].split('=')[1]
        else:
            splits = line.split("\t")
            if (splits[0].isdigit()):
                assert(sender_id is not None)
                train_id = int(splits[0])
                packet_id = int(splits[1])
                sent_time = int(splits[2])
                recv_time = int(splits[3])
                packet_size = int(splits[4])
                rtt = float(splits[5])
                train_size = int(splits[6])
                train_gap = int(splits[7])
                key = train_gap
                # key = train_size

                if (key != current_key):
                    current_count = 0
                    current_key = key
                    ignore_first_trains = int(IGNORE_FIRST_TRAINS_MS / train_gap)

                if (key not in relative_packet_arrivals):
                    relative_packet_arrivals[key] = dict()

                if (current_count < ignore_first_trains):
                    if (packet_id == 0):
                        current_count += 1
                else:
                    key2 = str(sender_id) + "-" + str(train_id)
                    if (key2 not in first_packets_arrivals):
                        first_packets_arrivals[key2] = recv_time
                        relative_packet_arrivals[key][key2] = []
                    relative_arrival_time = (recv_time - first_packets_arrivals[key2]) / 1e6
                    relative_packet_arrivals[key][key2].append(relative_arrival_time)
        line_num += 1

    file.close()
    return sender_id, relative_packet_arrivals

def analyze_different_train_gap(arrivals):
    z = 1.96
    # fig, ax = plt.subplots()
    # fig.set_size_inches(8, 6)
    # plt.margins(x=0, y=0)

    train_gaps = []
    train_gap_mean = dict()

    for train_gap in arrivals:
        train_gap = int(train_gap)
        arrival_of_last_packet = []
        for key in arrivals[train_gap]:
            arrival_of_last_packet.append(arrivals[train_gap][key][-1])

        mean = np.mean(arrival_of_last_packet)
        median = np.median(arrival_of_last_packet)
        stdev = np.std(arrival_of_last_packet)
        ci = z * stdev / math.sqrt(len(arrival_of_last_packet))
        train_gap_mean[train_gap] = (mean, ci)
        print("%d\t%.2f\t%.2f" % (train_gap, mean, ci))
    #     ax.errorbar(x=train_gap, y=mean, yerr=ci, color="black", fmt='o')
    
    # ax.set_ylabel('Relative arrival time of the last packet of the train (ms)')
    # ax.set_xlabel('Train gap')

    train_gap_mean = dict(sorted(train_gap_mean.items()))

    means = [value1 for value1, _ in train_gap_mean.values()]
    cis = [value1 for _, value1 in train_gap_mean.values()]
    train_gaps = list(train_gap_mean.keys())

    BASE_GAP = 100
    assert(BASE_GAP in train_gap_mean)

    (base_mean, base_ci) = train_gap_mean[BASE_GAP]
    gmin = min(train_gaps)
    for train_gap in train_gap_mean:
        (mean, ci) = train_gap_mean[train_gap]
        if (mean > base_mean * 0.7):
            gmin = train_gap
            break

    print("Suggested G_min = %d" % gmin)
    F = max(0, int(gmin - train_gap_mean[gmin][0]))
    print("Suggested F = %d" % F)
    
    # ax.set_xlim([0, np.max(train_gaps) * 1.1])
    # ax.set_ylim([0, (np.max(means) + np.max(cis)) * 1.1])
    # plt.show()

def analyze_train_gap_effect(trace_folder):
    # process download
    for packet_train_file in os.listdir(trace_folder):
        if ("dynamic" in packet_train_file):
            fpath = os.path.join(trace_folder, packet_train_file)
            if ("/client/" in trace_folder):
                sender_id, arrivals = process_dynamic_train_file(fpath)
            elif ("/server/" in trace_folder):
                sender_id, arrivals = process_server_train_file(fpath)
            down_train_config = analyze_different_train_gap(arrivals)

    return 0


def main(args):
    trace_folder = args[0]
    analyze_train_gap_effect(trace_folder)

if __name__ == '__main__':
    prog = sys.argv[0]
    args = sys.argv[1:]
    num_args = len(args)
    if (num_args < 1):
        sys.stderr.write((u"Usage: %s" +
                          u" <input-path>\n") %
                         (prog))
        sys.exit(1)
    
    main(args)