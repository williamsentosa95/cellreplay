import subprocess
import os
import numpy as np
import sys

# import matplotlib.pyplot as plt

# fig, ax = plt.subplots()
# fig.set_size_inches(10, 6)
# plt.margins(x=0, y=0)

# SMALL_SIZE = 8
# MEDIUM_SIZE = 15
# BIGGER_SIZE = 20

PACKET_SIZE = 1400

IGNORE_FIRST_TRAINS_MS = 100

def process_train_file(fpath):
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
    last_packets_arrivals = dict()
    ignored_keys = []
    
    current_key = None
    current_count = 0
    ignore_first_trains = 5

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
                # key = str(train_gap) + "-" + str(train_size)
                key = train_size
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
                    if (packet_id == 0):
                        first_packets_arrivals[key2] = recv_time
                        relative_packet_arrivals[key][key2] = []
                    if (key2 in first_packets_arrivals and key2 not in ignored_keys):
                        relative_arrival_time = (recv_time - first_packets_arrivals[key2]) / 1e6
                        relative_packet_arrivals[key][key2].append(relative_arrival_time)
        line_num += 1

    print(ignored_keys)

    file.close()
    return sender_id, relative_packet_arrivals

def process_all_train_file(folder_path):
    result = dict()
    for filename in sorted(os.listdir(folder_path)):
        if ("dynamictrain" in filename):
            file_path = folder_path + "/" + filename
            sender_id, relative_packet_arrivals = process_train_file(file_path)
            # print(relative_packet_arrivals)
            result = merge_two_dicts(result, relative_packet_arrivals)
    processed_result = process_relative_packet_arrivals(result)        
    return processed_result


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
                key = train_size

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
                            print("id=%d, last-to-first-gap=%d" % (train_id, last_to_first_gap))
                    relative_arrival_time = (recv_time - first_packets_arrivals[key2]) / 1e6
                    relative_packet_arrivals[key][key2].append(relative_arrival_time)
        line_num += 1

    file.close()
    return sender_id, relative_packet_arrivals

def merge_two_dicts(dict1, dict2):
    for idx in dict2:
        if (idx not in dict1):
            dict1[idx] = dict()
        for idx2 in dict2[idx]:
            if (idx2 not in dict1[idx]):
                dict1[idx][idx2] = []
            dict1[idx][idx2] = dict1[idx][idx2] + dict2[idx][idx2]
    return dict1

def process_relative_packet_arrivals(relative_packet_arrivals):
    result = dict()
    for key in relative_packet_arrivals:
        if (key not in result):
            result[key] = dict()
        num_trains = key
        for i in range(0, num_trains):
            total = 0
            count = 0
            arrivals = []
            for key2 in relative_packet_arrivals[key]:
                if (len(relative_packet_arrivals[key][key2]) > i):
                    arrivals.append(relative_packet_arrivals[key][key2][i])
            if (len(arrivals) > 0):
                result[key][i] = np.mean(arrivals)
                # result[key][i] = np.median(arrivals)
    return result

def process_all_train_file_server(folder_path):
    result = dict()
    for filename in sorted(os.listdir(folder_path)):
        if ("dynamic_packet_train" in filename):
            file_path = folder_path + "/" + filename
            sender_id, relative_packet_arrivals = process_server_train_file(file_path)
            result = merge_two_dicts(result, relative_packet_arrivals)
            # print(result)

    processed_result = process_relative_packet_arrivals(result)
    return processed_result

def extrapolate_to_length(arrivals, target_length, bw_mbps):
    curr_len = len(arrivals)
    start = arrivals[len(arrivals) - 1]
    results = arrivals
    required_time_to_deliver_a_packet = 1000 / (bw_mbps * 1e6 / 8 / 1400)
    for i in range(curr_len, target_length):
        ms = results[i - 1] + required_time_to_deliver_a_packet
        results[i] = ms
    return results

def calculate_completion_time_error(arrival_times, train_size_in_test):
    errors = dict()
    for key in sorted(arrival_times.keys()):
        gt = arrival_times[key][key - 1]
        estimated = arrival_times[train_size_in_test][key - 1]
        error = estimated - gt
        if (gt == 0):
            percentage_error = 0
        else:
            percentage_error = abs(error) / gt * 100 
        errors[key] = percentage_error
    return errors

def main(args):
    trace_folder = args[0]
    num_packets = []
    saturator_bw = float(args[1])
    # plt_save_file = trace_folder + "/../packet-train-traces.pdf"

    
    if ("/server/" in trace_folder):
        DOWNLOAD = False
    else:
        DOWNLOAD = True

    if (not DOWNLOAD):
        folder_path = os.path.join(trace_folder, "server_dynamic_packet_train_trace")
        relative_packet_arrivals = process_all_train_file_server(folder_path)
    else:
        for folder_name in sorted(os.listdir(trace_folder)):
            folder_path = os.path.join(trace_folder, folder_name)
            if ("PacketTrainEchoDynamic" in folder_name):
                relative_packet_arrivals = process_all_train_file(folder_path)



    plot_data = relative_packet_arrivals

    num_packets = list(plot_data.keys())
    max_num_packets = np.max(num_packets)

    for key in sorted(plot_data.keys()): 
        plot_data[key] = extrapolate_to_length(plot_data[key], max_num_packets, saturator_bw)

    best_num_train = -1
    lowest_error = None

    for key in sorted(plot_data.keys()): 
        errors = calculate_completion_time_error(plot_data, key)
        values = list(errors.values())
        mean_error = np.mean(values)
        print("If num train = %d, mean_error=%.2f" % (key, mean_error))
        
        if (best_num_train < 0):
            lowest_error = mean_error
            best_num_train = key
        else:
            if (mean_error < lowest_error):
                lowest_error = mean_error
                best_num_train = key
    
    print("Suggestion for train size = %d" % (best_num_train))


    # for key in sorted(plot_data.keys()): 
    #     num_packets = key
    #     pkt_idxes = list(plot_data[key].keys())
    #     arrival_times = list(plot_data[key].values())
    #     p = ax.plot(pkt_idxes[0:num_packets], arrival_times[0:num_packets], label=("train_size=%d") % (num_packets))
    #     ax.plot(pkt_idxes[num_packets:], arrival_times[num_packets:], linestyle="dashed", color=p[0].get_color())


    # ax.legend()
    # ax.set(ylabel="Packet arrival time relative to the first packet (ms)")
    # ax.set(xlabel="Packet sequence number")
    # plt.savefig(plt_save_file, bbox_inches='tight')
    # plt.show()

if __name__ == '__main__':
    prog = sys.argv[0]
    args = sys.argv[1:]
    num_args = len(args)
    if (num_args < 2) :
        sys.stderr.write((u"Usage: %s" +
                          u" <trace-folder> <Saturator-BW-Mbps>\n") %
                         (prog))
        sys.exit(1)

    main(args)