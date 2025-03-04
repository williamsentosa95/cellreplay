import argparse
import io
import json
import os
import sys
import socket
import time
import threading
import numpy as np
import struct
from datetime import datetime

CONTROL_SYN = 100
CONTROL_SYN_ACK = 150 
CONTROL_CONFIG_ACK = 170 
CONTROL_FIN = 200

PAYLOAD_PKT_INFO_START = 1
PAYLOAD_PKT_INFO_END = 2

UDP_HEADER_SIZE = 42
LOG_MS = 1000

curr_seq_num = 0
curr_train_num = 0
curr_train_initial_sent = 0
last_recv_client_packet = 0
stop_sending = False

sleepEvent = threading.Event()

RANDOM_PAYLOADS = []
RANDOM_IDX = 0
MAX_PACKET_SIZE = 1500

CONTROL_PACKET_LENGTH = 8 * 3

def parse_packet(packet):
    seq_num = int.from_bytes(packet[0:8], byteorder='big')
    sent_timestamp = int.from_bytes(packet[8:16], byteorder='big')
    recv_timestamp = int.from_bytes(packet[16:24], byteorder='big')
    sender_id = int.from_bytes(packet[24:32], byteorder='big')
    train_num = int.from_bytes(packet[32:40], byteorder='big')
    pkt_info = int.from_bytes(packet[40:48], byteorder='big')
    return seq_num, sent_timestamp, recv_timestamp, sender_id, train_num, pkt_info

def parse_control_packet(packet):
    sender_id = int.from_bytes(packet[0:8], byteorder='big')
    content = int.from_bytes(packet[8:16], byteorder='big')
    exp_id = int.from_bytes(packet[16:24], byteorder='big')
    return sender_id, content, exp_id

def parse_config_packet(packet):
    sender_id = int.from_bytes(packet[0:8], byteorder='big')
    upload_gap_ms = int.from_bytes(packet[8:16], byteorder='big')
    download_gap_ms = int.from_bytes(packet[16:24], byteorder='big')
    upload_num_packets = int.from_bytes(packet[24:32], byteorder='big')
    download_num_packets = int.from_bytes(packet[32:40], byteorder='big')
    packet_size = int.from_bytes(packet[40:48], byteorder='big')
    return sender_id, upload_gap_ms, download_gap_ms, upload_num_packets, download_num_packets, packet_size

def is_config_packet(packet):
    return len(packet) == 8 * 6

def is_control_packet(packet):
    return len(packet) == CONTROL_PACKET_LENGTH

def create_control_packet(sender_id, content):
    packet = bytearray(16)
    packet_bytes = sender_id.to_bytes(8, 'big')
    packet[0:8] = packet_bytes[0:8]
    packet_bytes = content.to_bytes(8, 'big')
    packet[8:16] = packet_bytes[0:8]
    return packet

def add_recv_time(packet, recv_time):
    packet_bytes = recv_time.to_bytes(8, 'big')
    new_packet = bytearray(packet)
    for i in range(16, 24):
        new_packet[i] = packet_bytes[i - 16]
    return new_packet


def get_random_bytes(size):
    global RANDOM_IDX
    global RANDOM_PAYLOADS
    assert(size <= MAX_PACKET_SIZE)
    result = RANDOM_PAYLOADS[RANDOM_IDX][0:size]
    RANDOM_IDX = (RANDOM_IDX + 1) % len(RANDOM_PAYLOADS)
    return result

def create_data_packet(sender_id, packet_size, seq_num, train_num, pkt_info, initial_send_timestamp):
    packet = bytearray(packet_size - UDP_HEADER_SIZE)
    packet_bytes = seq_num.to_bytes(8, 'big')
    packet[0:8] = packet_bytes[0:8]
    packet_bytes = initial_send_timestamp.to_bytes(8, 'big')
    packet[8:16] = packet_bytes[0:8]
    packet_bytes = sender_id.to_bytes(8, 'big')
    packet[24:32] = packet_bytes[0:8]
    packet_bytes = train_num.to_bytes(8, 'big')
    packet[32:40] = packet_bytes[0:8]
    packet_bytes = pkt_info.to_bytes(8, 'big')
    packet[40:48] = packet_bytes[0:8]
    random_content_size = packet_size - UDP_HEADER_SIZE - (6 * 8)
    packet_bytes = get_random_bytes(random_content_size)
    packet[48:] = packet_bytes[0:random_content_size]
    # Fill in the packet info
    return packet

def create_info_packet(sender_id, curr_time, avg_throughput):
    packet = bytearray(24)
    packet_bytes = sender_id.to_bytes(8, 'big')
    packet[0:8] = packet_bytes[0:8]
    packet_bytes = curr_time.to_bytes(8, 'big')
    packet[8:16] = packet_bytes[0:8]
    packet_bytes = bytearray(struct.pack(">d", avg_throughput))
    packet[16:24] = packet_bytes[0:8]
    return packet

def create_random_bytes_dict(n):
    global RANDOM_PAYLOADS
    global RANDOM_IDX
    RANDOM_PAYLOADS = []
    RANDOM_IDX = 0
    for i in range(0, n):
        RANDOM_PAYLOADS.append(os.urandom(MAX_PACKET_SIZE))
    return RANDOM_PAYLOADS

def create_log_dir(trace_folder, exp_name, exp_id):
    if (not os.path.exists(trace_folder)):
        os.mkdir(trace_folder)

    now = datetime.now()
    date_folder = trace_folder + "/" + now.strftime("%m-%d-%Y")
    if (not os.path.exists(date_folder)):
        os.mkdir(date_folder)

    if (exp_id > 0):
        exp_folder = date_folder + "/" + str(exp_id)
    else:
        exp_folder = date_folder + "/default"
    if (not os.path.exists(exp_folder)):
        os.mkdir(exp_folder)

    exp_trace_folder = exp_folder + "/" + exp_name
    if (not os.path.exists(exp_trace_folder)):
        os.mkdir(exp_trace_folder)
        
    log_file_name = exp_name + "_" + now.strftime("%H-%M-%S") + ".txt"
    return exp_trace_folder + "/" + log_file_name

# Terminate if :
# 1. it does not receive packets from the client for 3 seconds
# 2. Client send FIN
def terminate_client_connection(sender_id, address):
    print("Connection terminated by id=%d, %s" % (sender_id, address))
    # Reset all of the variables
    # Close the log

def send_packet_train_thread(socket, sender_id, address, gap_ms, num_packets, packet_size):
    global last_recv_client_packet
    global stop_sending
    global curr_train_num
    global curr_train_initial_sent
    try:
        while (True):
            # Add randomness to it
            if (stop_sending):
                print("Stop sending because of FIN!")
                break

            seq_num = 0
            now = time.time_ns()
            for i in range(0, num_packets):
                if (i == 0):
                    pkt_info = PAYLOAD_PKT_INFO_START
                elif (i == num_packets - 1):
                    pkt_info = PAYLOAD_PKT_INFO_END
                else:
                    pkt_info = 0
    
                pkt = create_data_packet(sender_id, packet_size, seq_num, curr_train_num, pkt_info, curr_train_initial_sent)
                socket.sendto(pkt, address)
                seq_num = seq_num + 1
            
            curr_train_num += 1

            if ((now - last_recv_client_packet) > 3 * 1e9):
                print("Stop sending due to client (id = %d) inactivity!" % (sender_id))
                break

            sleepEvent.clear()
            sleepEvent.wait(3)
    finally:
        print("Sending to %d has stopped!" % (sender_id))

def print_throughputs(throughputs):
    text = "["
    for i in range(0, len(throughputs)):
        text += "{:.2f}".format(throughputs[i]) + ","
    text += "]"
    return text

def main(args):
    global last_recv_client_packet
    global curr_train_initial_sent
    global stop_sending

    global random_bytes

    server_ip = args[0]
    server_port = int(args[1])
    trace_folder = args[2]

    # Populate random bytes of packets
    create_random_bytes_dict(1000)

    # localIP     = socket.gethostbyname(socket.gethostname())
    localIP = server_ip
    bufferSize  = 2048
    
    # Create a datagram socket
    UDPServerSocket = socket.socket(family=socket.AF_INET, type=socket.SOCK_DGRAM)

    # Bind to address and ip
    UDPServerSocket.bind((localIP, server_port))
    print("PacketTrain UDP server is up, IP=%s, port=%d ..." % (localIP, server_port))
    
    curr_sender_id = 0
    curr_upload_gap_ms = 0
    curr_download_gap_ms = 0
    curr_upload_num_packets = 0
    curr_download_num_packets = 0
    curr_packet_size = 0
    send_thread = None

    last_sent_time = 0
    start_sent_time = 0
    start_sent_time_2 = 0
    last_recv_time = 0
    start_recv_time = 0
    log_time = 0
    count_packet = 0
    is_next_second_packet = False

    curr_train_num = 0

    throughputs = []
    curr_log_timestamp = 0

    log_file = None

    exp_id = 0
        

    # Listen for incoming datagrams
    while(True):
        message, address = UDPServerSocket.recvfrom(bufferSize)
        if (is_control_packet(message)):
            sender_id, content, exp_id = parse_control_packet(message)
            if (content == CONTROL_SYN):
                pkt = create_control_packet(sender_id, CONTROL_SYN_ACK)
                UDPServerSocket.sendto(pkt, address)
                #### RESET VARIABLES
                curr_sender_id = 0
                curr_upload_gap_ms = 0
                curr_download_gap_ms = 0
                curr_upload_num_packets = 0
                curr_download_num_packets = 0
                curr_packet_size = 0
                curr_train_initial_sent = 0

                last_sent_time = 0
                start_sent_time = 0
                start_sent_time_2 = 0
                last_recv_time = 0
                start_recv_time = 0
                log_time = 0
                count_packet = 0
                is_next_second_packet = False
                stop_sending = True
                ########
                throughputs = []
                curr_log_timestamp = 0
                curr_sender_id = sender_id

                if (log_file is not None):
                    log_file.close()                

            elif (content == CONTROL_FIN):
                print("Connection terminated by id=%d, %s" % (sender_id, address))
                #### RESET VARIABLES
                curr_sender_id = 0
                curr_upload_gap_ms = 0
                curr_download_gap_ms = 0
                curr_upload_num_packets = 0
                curr_download_num_packets = 0
                curr_packet_size = 0
                curr_train_initial_sent = 0

                last_sent_time = 0
                start_sent_time = 0
                start_sent_time_2 = 0
                last_recv_time = 0
                start_recv_time = 0
                log_time = 0
                count_packet = 0
                is_next_second_packet = False
                ########
                stop_sending = True
                sleepEvent.set()

        elif (is_config_packet(message)):
            # parse control packets
            sender_id, upload_gap_ms, download_gap_ms, upload_num_packets, download_num_packets, packet_size = parse_config_packet(message)
            if (curr_sender_id != sender_id):
                print("Got other sender_id!!")
            else:
                curr_upload_gap_ms = upload_gap_ms
                curr_download_gap_ms = download_gap_ms
                curr_upload_num_packets = upload_num_packets
                curr_download_num_packets = download_num_packets
                curr_packet_size = packet_size
                # create log file
                log_file = open(create_log_dir(trace_folder, "server_packet_train_trace", exp_id), "w")
                log_file.write("%s : sender_id=%d train_gap_ms=%d up_num=%d down_num=%d size=%d\n" % (address, sender_id, curr_upload_gap_ms, curr_upload_num_packets, curr_download_num_packets, curr_packet_size))
                log_file.write("TRAIN_NUM\tSEQ_NUM\tSEND_TIMESTAMP\tRECV_TIMESTAMP\tSENDER_ID\tPACKET_SIZE\n")
                log_file.flush()
                # send ACK to client
                pkt = create_control_packet(sender_id, CONTROL_CONFIG_ACK)
                UDPServerSocket.sendto(pkt, address)
                print("%s : sender_id=%d up_gap_ms=%d down_gap_ms=%d up_num=%d down_num=%d size=%d" % (address, sender_id, curr_upload_gap_ms, curr_download_gap_ms, curr_upload_num_packets, curr_download_num_packets, curr_packet_size))
                last_recv_client_packet = time.time_ns()
                send_thread = threading.Thread(target=send_packet_train_thread, args=(UDPServerSocket, curr_sender_id, address, curr_download_gap_ms, curr_download_num_packets, curr_packet_size))
        else:
            seq_num, sent_timestamp, recv_timestamp, sender_id, train_num, pkt_info = parse_packet(message)
            now = time.time_ns()
            
            log_file.write("%d\t%d\t%d\t%d\t%d\t%d\n" % (train_num, seq_num, sent_timestamp, now, sender_id, len(message)))
            
            if (sender_id == curr_sender_id):
                if (pkt_info == PAYLOAD_PKT_INFO_START):
                    count_packet = 0
                    start_recv_time = now
                    curr_train_num = train_num
                    curr_train_initial_sent = sent_timestamp
                    # Send a train to client
                    if (stop_sending == True):
                        stop_sending = False
                        send_thread.start()
                    else:
                        sleepEvent.set()
                elif (pkt_info == PAYLOAD_PKT_INFO_END):
                    # reset all parameters
                    if (train_num == curr_train_num and start_recv_time > 0):
                        # Calculate the estimated bandwidth
                        sending_duration = now - start_recv_time
                        amount_of_traffic = count_packet * curr_packet_size
                        bandwidth_mbps = ((amount_of_traffic * 8) / (sending_duration / 1e9)) / 1e6
                        # print(("Train #%d - #%d - %.2f Mbps") % (train_num, curr_packet_size, bandwidth_mbps))
                        throughputs.append(bandwidth_mbps)
                        # resetting parameters
                        count_packet = 0
                        start_recv_time = 0
                    else:
                        print("WARNING, recv train_num = %d, curr train_num = %d" % (train_num, curr_train_num))
                else:
                    count_packet = count_packet + 1

            # Logging
            if (curr_log_timestamp == 0):
                curr_log_timestamp = now
            if ((now - curr_log_timestamp) / 1e6 >= LOG_MS):
                if (len(throughputs) > 0):
                    avg_throughput = np.mean(throughputs)
                    # print(print_throughputs(throughputs))
                    print("Upload - Time : %d: #throughputs=%d, avg_throughputs=%.2f" % (log_time, len(throughputs), avg_throughput))
                    
                    log_file.flush()
                    # Send info packet here
                    pkt = create_info_packet(sender_id, log_time, avg_throughput)
                    UDPServerSocket.sendto(pkt, address)
                    throughputs = []
                    log_time += 1
                    curr_log_timestamp = now

            # Logging the last time we receive a packet from client
            last_recv_client_packet = now
            

if __name__ == '__main__':
    prog = sys.argv[0]
    num_args = len(sys.argv)

    if (num_args < 4):
        sys.stderr.write((u"Usage: %s" +
                          u" <server-ip> <server-port> <trace-folder> ...\n") %
                         (prog))
        sys.exit(1)

    args = sys.argv[1:]
    main(args)
