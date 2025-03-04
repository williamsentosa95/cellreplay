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

UDP_HEADER_SIZE = 42
LOG_MS = 1000

curr_seq_num = 0
last_recv_client_packet = 0
stop_sending = False

MIN_SLEEP_TIME_MS = 1
MAX_PACKET_SIZE = 1500

RANDOM_PAYLOADS = []
RANDOM_IDX = 0

CONTROL_PACKET_LENGTH = 8 * 3

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

def parse_packet(packet):
    seq_num = int.from_bytes(packet[0:8], byteorder='big')
    sent_timestamp = int.from_bytes(packet[8:16], byteorder='big')
    recv_timestamp = int.from_bytes(packet[16:24], byteorder='big')
    sender_id = int.from_bytes(packet[24:32], byteorder='big')
    return seq_num, sent_timestamp, recv_timestamp, sender_id

def parse_control_packet(packet):
    sender_id = int.from_bytes(packet[0:8], byteorder='big')
    content = int.from_bytes(packet[8:16], byteorder='big')
    exp_id = int.from_bytes(packet[16:24], byteorder='big')
    return sender_id, content, exp_id

def parse_config_packet(packet):
    sender_id = int.from_bytes(packet[0:8], byteorder='big')
    upload_rate = int.from_bytes(packet[8:16], byteorder='big')
    download_rate = int.from_bytes(packet[16:24], byteorder='big')
    packet_size = int.from_bytes(packet[24:32], byteorder='big')
    return sender_id, upload_rate, download_rate, packet_size

def is_config_packet(packet):
    return len(packet) == 8 * 4;

def is_control_packet(packet):
    return len(packet) == CONTROL_PACKET_LENGTH;

def create_control_packet(sender_id, content):
    packet = bytearray(16);
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

def create_data_packet(sender_id, packet_size, now):
    global curr_seq_num
    
    packet = bytearray(packet_size - UDP_HEADER_SIZE)
    packet_bytes = curr_seq_num.to_bytes(8, 'big')
    packet[0:8] = packet_bytes[0:8]
    packet_bytes = now.to_bytes(8, 'big')
    packet[8:16] = packet_bytes[0:8]
    packet_bytes = sender_id.to_bytes(8, 'big')
    packet[24:32] = packet_bytes[0:8]
    random_content_size = packet_size - UDP_HEADER_SIZE - (4 * 8)
    # Fill in with random_bytes
    packet_bytes = get_random_bytes(random_content_size)
    packet[32:] = packet_bytes[0:random_content_size]
    curr_seq_num = curr_seq_num + 1
    # Fill in the packet info
    return packet

def send_download_saturator_thread(socket, sender_id, address, download_rate, packet_size):
    global last_recv_client_packet
    global stop_sending

    packet_per_second = int((download_rate * 1000000 / 8) / packet_size);
    amount_of_batches_per_second = int(1000 / MIN_SLEEP_TIME_MS);
    packet_per_batch_lower = int(packet_per_second / amount_of_batches_per_second);
    packet_per_batch_upper = packet_per_batch_lower + 1;
    send_upper = int(packet_per_second - (amount_of_batches_per_second * packet_per_batch_lower));
    send_lower = int(amount_of_batches_per_second - send_upper);
    try:
        while (True):
            if (stop_sending):
                break
            for i in range(0, send_lower):
                time1 = time.time_ns()
                for j in range(0, packet_per_batch_lower):
                    now = time.time_ns()
                    pkt = create_data_packet(sender_id, packet_size, now)
                    socket.sendto(pkt, address)
                time2 = time.time_ns()
                sleep_time = MIN_SLEEP_TIME_MS - (time2 - time1) / 1000000
                # print(sleep_time)
                if (sleep_time > 0):
                    time.sleep(sleep_time / float(1000))
            for i in range(0, send_upper):
                time1 = time.time_ns()
                for j in range(0, packet_per_batch_upper):
                    now = time.time_ns()
                    pkt = create_data_packet(sender_id, packet_size, now)
                    socket.sendto(pkt, address)
                time2 = time.time_ns()
                sleep_time = MIN_SLEEP_TIME_MS - (time2 - time1) / 1000000
                if (sleep_time > 0):
                    time.sleep(sleep_time / float(1000))
            
            if ((time.time_ns() - last_recv_client_packet) > 3 * 1e9):
                print("Stop sending due to client (id = %d) inactivity!" % (sender_id))
                break            
    finally:
        print("Sending to %d has stopped!" % (sender_id))

def create_info_packet(sender_id, curr_time, avg_throughput):
    packet = bytearray(24);
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


def main(args):
    global last_recv_client_packet
    global stop_sending

    server_ip = args[0]
    server_port = int(args[1])
    trace_folder = args[2]

    create_random_bytes_dict(1000)

    # localIP     = socket.gethostbyname(socket.gethostname())
    localIP = server_ip
    bufferSize  = 2048
    
    # Create a datagram socket
    UDPServerSocket = socket.socket(family=socket.AF_INET, type=socket.SOCK_DGRAM)

    # Bind to address and ip
    UDPServerSocket.bind((localIP, server_port))
    print("Saturator UDP server is up, IP=%s, port=%d ..." % (localIP, server_port))
    
    curr_sender_id = 0
    curr_upload_rate = 0
    curr_download_rate = 0
    curr_packet_size = 0
    send_thread = None

    log_time = 0
    count_packet = 0

    curr_log_timestamp = 0
    log_file = None

    exp_id = 0

    # Listen for incoming datagrams
    while(True):
        message, address = UDPServerSocket.recvfrom(bufferSize)
        if (is_control_packet(message)):
            sender_id, content, exp_id = parse_control_packet(message)
            print("%d %d" % (sender_id, content))
            if (content == CONTROL_SYN):
                # create a thread to handle all communication to this client.
                pkt = create_control_packet(sender_id, CONTROL_SYN_ACK)
                UDPServerSocket.sendto(pkt, address)
                curr_sender_id = sender_id
                #### RESET VARIABLES
                curr_upload_rate = 0
                curr_download_rate = 0
                curr_packet_size = 0
                log_time = 0
                count_packet = 0
                curr_log_timestamp = 0
                stop_sending = False

                if (log_file is not None):
                    log_file.close()

                ########
            elif (content == CONTROL_FIN):
                print("Connection terminated by id=%d, %s" % (sender_id, address));
                #log_file.close()
                #### RESET VARIABLES
                curr_sender_id = 0
                curr_upload_rate = 0
                curr_download_rate = 0
                curr_packet_size = 0
                log_time = 0
                count_packet = 0
                curr_log_timestamp = 0
                ########
                stop_sending = True

        elif (is_config_packet(message)):
            # parse control packets
            sender_id, upload_rate, download_rate, packet_size = parse_config_packet(message)
            if (curr_sender_id != sender_id):
                print("Got other sender_id!!")
            else:
                last_recv_client_packet = time.time_ns()
                curr_sender_id = sender_id
                curr_upload_rate = upload_rate
                curr_download_rate = download_rate
                curr_packet_size = packet_size
                # send ACK to client
                pkt = create_control_packet(sender_id, CONTROL_CONFIG_ACK)
                UDPServerSocket.sendto(pkt, address)
                print("%s : %d %d %d %d" % (address, sender_id, upload_rate, download_rate, packet_size))
                log_file = open(create_log_dir(trace_folder, "saturator_trace", exp_id), "w")
                log_file.write("%s : sender_id=%d upload_rate=%d download_rate=%d packet_size=%d\n" % (address, sender_id, upload_rate, download_rate, packet_size))
                log_file.write("SEQ_NUM\tSEND_TIMESTAMP\tRECV_TIMESTAMP\tSENDER_ID\tPACKET_SIZE\n")
                log_file.flush()
                # Start sending data thread
                stop_sending = False
                send_thread = threading.Thread(target=send_download_saturator_thread, args=(UDPServerSocket, curr_sender_id, address, curr_download_rate, curr_packet_size))
                send_thread.start()
        else:
            seq_num, sent_timestamp, recv_timestamp, sender_id = parse_packet(message)
            if (sender_id == curr_sender_id):
                now = time.time_ns();
                log_file.write("%d\t%d\t%d\t%d\t%d\n" % (seq_num, sent_timestamp, now, sender_id, len(message)))
                count_packet += 1
                if (curr_log_timestamp == 0):
                    curr_log_timestamp = now
                if ((now - curr_log_timestamp) / 1e6 >= LOG_MS):
                    throughput = (count_packet * curr_packet_size * 8 / 1e6) / (LOG_MS / 1000)
                    print("Upload - Time : %d: #throughput=%.2f" % (log_time, throughput))
                    print("Num packets received = " + str(count_packet))
                    log_file.flush()
                    pkt = create_info_packet(sender_id, log_time, throughput)
                    UDPServerSocket.sendto(pkt, address)
                    log_time += 1
                    curr_log_timestamp = now
                    count_packet = 0
                last_recv_client_packet = now
            else:
                if (curr_sender_id > 0):
                    print("Got somebody's else packet!!")

            

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
