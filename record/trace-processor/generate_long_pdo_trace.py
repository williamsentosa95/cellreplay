import subprocess
import os
# import numpy as np
import sys

# import matplotlib.pyplot as plt

# fig, ax = plt.subplots()
# fig.set_size_inches(20, 6)
# plt.margins(x=0, y=0)

SMALL_SIZE = 8
MEDIUM_SIZE = 15
BIGGER_SIZE = 20

START_TIME_MS = 2000

def generate_bandwidth_trace(fpath):
    throughputs = []
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
                    
                    if (relative_recv_time > START_TIME_MS):
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
                              throughput = ((curr_traffic * 8) / 1e6)
                              throughputs.append(throughput)
                              times.append(time)
                              time = time + 1
                              curr_traffic = 0
                              start_time = recv_timestamp

    return trace, times, throughputs

def normalize_trace(bandwidth_trace, num_packets, curr_time, upper_end):
  
    if (num_packets <= 0):
        return bandwidth_trace

    duration = upper_end - curr_time
    packet_gap = float(duration) / float(num_packets)
    timestamp = float(curr_time)
    bandwidth_trace.append(curr_time)
    for i in range(1, num_packets):
        bandwidth_trace.append(round(timestamp + packet_gap))
        timestamp = timestamp + packet_gap
    
    return bandwidth_trace

def generate_normalized_bandwidth_trace(trace):
    normalize_gap_ms = 1000
    bandwidth_trace = []
    curr_time = 0
    upper_end = normalize_gap_ms
    num_packets = 0
    stored_packets = 0

    for timestamp in trace:
        num_packets = num_packets + 1
        if (timestamp >= upper_end):
            packets = num_packets - stored_packets
            bandwidth_trace = normalize_trace(bandwidth_trace, packets, curr_time, upper_end)
            curr_time = upper_end
            upper_end = upper_end + normalize_gap_ms
            stored_packets = num_packets

    return bandwidth_trace


def main(args):
    data = args[0]
    outpath = args[1]
    plot_file = None

    # if (len(args) > 2):
    #     plot_file = args[2]
    
    trace, times, throughputs = generate_bandwidth_trace(data)
    
    outfile = open(outpath, "w")
    for i in range(0, len(trace)):
        outfile.write(("%d\n") % (trace[i])) 
    outfile.close()

    # if (plot_file is not None):
    #     # Plot to file
    #     plt.plot(times, throughputs)

    #     # print(throughputs)
    #     plt.grid(True)

    #     ax.set_ylabel('Throughputs (Mbps)', fontsize=BIGGER_SIZE)
    #     ax.set_xlabel('Time (s)', fontsize=BIGGER_SIZE)

    #     ax.set_ylim(0, np.max(throughputs) * 1.1)

    #     plt.xticks(fontsize=BIGGER_SIZE)
    #     plt.yticks(fontsize=BIGGER_SIZE)

    #     plt.savefig(plot_file, bbox_inches='tight')        

if __name__ == '__main__':
    prog = sys.argv[0]
    args = sys.argv[1:]
    num_args = len(args)
    if (num_args < 2) :
        sys.stderr.write((u"Usage: %s" +
                          u" <bandwidth-trace> <out-file>\n") %
                         (prog))
        sys.exit(1)

    main(args)