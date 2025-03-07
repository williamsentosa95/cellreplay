import sys
import os

# import matplotlib.pyplot as plt

# fig, ax = plt.subplots()
# fig.set_size_inches(20, 6)
# plt.margins(x=0, y=0)

SMALL_SIZE = 8
MEDIUM_SIZE = 15
BIGGER_SIZE = 20

START_TIME_MS = 2000

def process_down_train_file(fpath):
    file = open(fpath)
    
    pdos = dict()
    rtts = dict()
    times = dict()
    first_packet_recv_time = dict()

    base_send_time = -1
    curr_train_id = -1
    base_train_id = -1
    sender_id = -1

    for line in file.readlines():
        if ("=" in line):
            # Process header data
            splits = line.split(",")
            sender_id = splits[0].split("=")[1]
            server_ip = splits[1].split("=")[1]
            packet_size = int(splits[2].split("=")[1])
            gap_ms = int(splits[3].split("=")[1])
            up_num_packets = int(splits[5].split("=")[1])
            down_num_packets = int(splits[6].split("=")[1])
        elif ("\t" in line):
            # Process download/upload
            splits = line.split("\t")
            if (splits[0].isdigit()):
                train_id = int(splits[0])
                seq_id = int(splits[1])
                sent_time = int(splits[2])
                recv_time = int(splits[3])

            if (seq_id == 0):
                if (base_send_time < 0):
                    base_send_time = sent_time
                if (base_train_id < 0):
                    base_train_id = train_id
                train_id = train_id - base_train_id
                rtt = (recv_time - sent_time) / 1e6
                rtts[train_id] = rtt
                time = (sent_time - base_send_time) / 1e6
                times[train_id] = time
                pdos[train_id] = [0]
                first_packet_recv_time[train_id] = recv_time
            else:
                train_id = train_id - base_train_id
                if (train_id in first_packet_recv_time):
                    interarrival = (recv_time - first_packet_recv_time[train_id]) / 1e6
                    pdos[train_id].append(int(interarrival))


    return sender_id, times, rtts, pdos

def process_up_train_file(fpath, down_sender_id):
    file = open(fpath)
    
    pdos = dict()
    first_packet_recv_time = dict()

    base_send_time = -1
    curr_train_id = -1
    base_train_id = -1

    for line in file.readlines():
        if ("=" in line):
            line = line.split(":")[1]
            # Process header data
            splits = line.split(" ")
            sender_id = splits[1].split("=")[1]
            gap_ms = int(splits[2].split("=")[1])
            up_num_packets = int(splits[3].split("=")[1])
            down_num_packets = int(splits[4].split("=")[1])
            packet_size = int(splits[5].split("=")[1])
            assert(down_sender_id == sender_id)
        elif ("\t" in line):
            # Process download/upload
            splits = line.split("\t")
            if (splits[0].isdigit()):
                train_id = int(splits[0])
                seq_id = int(splits[1])
                sent_time = int(splits[2])
                recv_time = int(splits[3])

                if (seq_id == 0):
                    if (base_send_time < 0):
                        base_send_time = sent_time
                    if (base_train_id < 0):
                        base_train_id = train_id
                    train_id = train_id - base_train_id
                    pdos[train_id] = [0]
                    first_packet_recv_time[train_id] = recv_time
                else:
                    train_id = train_id - base_train_id
                    if (train_id in first_packet_recv_time):
                        interarrival = (recv_time - first_packet_recv_time[train_id]) / 1e6
                        pdos[train_id].append(int(interarrival))


    return pdos

def print_array(arr):
    res = ""
    for i in range(0, len(arr) - 1):
        res = res + str(arr[i]) + " "
    res = res + str(arr[len(arr) - 1])
    return res

def cut_trace(times, rtts, down_pdos, up_pdos, start_time_ms):
    result_times = []
    result_rtts = []
    result_down_pdos = []
    result_up_pdos = []
    trace_start_time = -1
    for train_id in times:
        if (times[train_id] >= start_time_ms):
            if (trace_start_time < 0):
                trace_start_time = times[train_id]
            if (train_id in times and train_id in rtts and train_id in down_pdos and train_id in up_pdos):
                result_times.append(times[train_id] - trace_start_time)
                result_rtts.append(rtts[train_id])
                result_down_pdos.append(down_pdos[train_id])
                result_up_pdos.append(up_pdos[train_id])
    return result_times, result_rtts, result_down_pdos, result_up_pdos

def main(args):
    up_train_trace = args[0]
    down_train_trace = args[1]
    up_output = args[2]
    down_output = args[3]

    out_plot = None

    # if (len(args) > 2):
    #     out_plot = args[2]
    print(down_train_trace)
    sender_id, temp_times, temp_rtts, temp_down_pdos = process_down_train_file(down_train_trace)
    temp_up_pdos = process_up_train_file(up_train_trace, sender_id)
    times, rtts, down_pdos, up_pdos = cut_trace(temp_times, temp_rtts, temp_down_pdos, temp_up_pdos, START_TIME_MS)
    assert(len(times) == len(rtts) and len(times) == len(down_pdos) and len(times) == len(up_pdos))

    # write to file
    outfile_up = open(up_output, "w")
    outfile_down = open(down_output, "w")
    for i in range(0, len(times)):
        one_way_ms = int(rtts[i] / 2)
        outfile_up.write(("%d %d %s\n") % (times[i], one_way_ms, print_array(up_pdos[i])))
        outfile_down.write(("%d %d %s\n") % (times[i], one_way_ms, print_array(down_pdos[i])))    
    outfile_up.close()
    outfile_down.close()

    # if (out_plot is not None):
    #     # Plot to file
    #     time_seconds = []
    #     for time in times:
    #         time_seconds.append(time / 1000) 

    #     plt.plot(time_seconds, rtts)

    #     plt.grid(True)

    #     ax.set_ylabel('RTT (s)', fontsize=BIGGER_SIZE)
    #     ax.set_xlabel('Time (s)', fontsize=BIGGER_SIZE)

    #     plt.xticks(fontsize=BIGGER_SIZE)
    #     plt.yticks(fontsize=BIGGER_SIZE)

    #     plt.savefig(out_plot, bbox_inches='tight') 


if __name__ == '__main__':
    prog = sys.argv[0]
    num_args = len(sys.argv)

    if (num_args < 5):
        # sys.stderr.write((u"Usage: %s" +
        #                   u" <upload-train-trace> <download-train-trace> <output-up-trace> <output-down-trace> <optional: output-pdf>\n") %
        #                  (prog))
        sys.stderr.write((u"Usage: %s" +
                          u" <upload-train-trace> <download-train-trace> <output-up-trace> <output-down-trace>\n") %
                         (prog))
        sys.exit(1)

    args = sys.argv[1:]
    main(args)