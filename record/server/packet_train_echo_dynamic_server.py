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
last_recv_client_packet = 0
stop_sending = False


RANDOM_PAYLOADS = []
RANDOM_IDX = 0
MAX_PACKET_SIZE = 1500

CONTROL_PACKET_LENGTH = 8 * 3

MAX_ACK_SIZE = 1400 - UDP_HEADER_SIZE

PACKET_TRAIN_ECHO_DYANMIC_SIGNATURE = 1234
GAP_BETWEEN_EXPERIMENT = 1000
TRAIN_SENDING_DURATION = 3000

def parse_packet(packet):
    seq_num = int.from_bytes(packet[0:8], byteorder='big')
    sent_timestamp = int.from_bytes(packet[8:16], byteorder='big')
    recv_timestamp = int.from_bytes(packet[16:24], byteorder='big')
    sender_id = int.from_bytes(packet[24:32], byteorder='big')
    train_num = int.from_bytes(packet[32:40], byteorder='big')
    pkt_info = int.from_bytes(packet[40:48], byteorder='big')
    train_size = int.from_bytes(packet[48:56], byteorder='big')
    train_gap_ms = int.from_bytes(packet[56:64], byteorder='big')
    return seq_num, sent_timestamp, recv_timestamp, sender_id, train_num, pkt_info, train_size, train_gap_ms

def parse_control_packet(packet):
    sender_id = int.from_bytes(packet[0:8], byteorder='big')
    content = int.from_bytes(packet[8:16], byteorder='big')
    exp_id = int.from_bytes(packet[16:24], byteorder='big')
    return sender_id, content, exp_id

def stringToArray(str_input):
    print("Str input = " + str_input)
    splits = str_input.split(",")
    print(splits)
    result = []
    for split in splits:
        result.append(int(split))
    return result

def parse_config_packet(packet):
    sender_id = int.from_bytes(packet[8:16], byteorder='big')
    packet_size = int.from_bytes(packet[16:24], byteorder='big')
    upload = int.from_bytes(packet[24:32], byteorder='big')
    echo_to_only_first_and_tail = int.from_bytes(packet[32:40], byteorder='big')

    exp_configs = str(packet[40:].decode()).rstrip('\x00')
    print("Pkt config = %s" % (exp_configs))
    splits = exp_configs.split("-")
    gaps = stringToArray(splits[0])
    num_packets = stringToArray(splits[1])
    print("gaps = " + str(gaps))
    print("num_packets = " + str(num_packets))
    
    return sender_id, gaps, upload, num_packets, packet_size, echo_to_only_first_and_tail

def is_config_packet(packet):
    signature = int.from_bytes(packet[0:8], byteorder='big')
    return signature == PACKET_TRAIN_ECHO_DYANMIC_SIGNATURE

def is_control_packet(packet):
    return len(packet) == CONTROL_PACKET_LENGTH;

def create_control_packet(sender_id, content):
    packet = bytearray(16);
    packet_bytes = sender_id.to_bytes(8, 'big')
    packet[0:8] = packet_bytes[0:8]
    packet_bytes = content.to_bytes(8, 'big')
    packet[8:16] = packet_bytes[0:8]
    return packet

def add_recv_time_and_trim(packet, recv_time):
    packet_bytes = recv_time.to_bytes(8, 'big')
    new_packet = bytearray(packet)
    for i in range(16, 24):
        new_packet[i] = packet_bytes[i - 16]
    if len(new_packet) > MAX_ACK_SIZE:
        return new_packet[:MAX_ACK_SIZE]
    else:
        return new_packet


def get_random_bytes(size):
    global RANDOM_IDX
    global RANDOM_PAYLOADS
    assert(size <= MAX_PACKET_SIZE)
    result = RANDOM_PAYLOADS[RANDOM_IDX][0:size]
    RANDOM_IDX = (RANDOM_IDX + 1) % len(RANDOM_PAYLOADS)
    return result

def create_data_packet(sender_id, packet_size, seq_num, train_num, pkt_info, train_size, train_gap_ms, initial_send_timestamp):
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
    packet_bytes = train_size.to_bytes(8, 'big')
    packet[48:56] = packet_bytes[0:8]
    packet_bytes = train_gap_ms.to_bytes(8, 'big')
    packet[56:64] = packet_bytes[0:8]
    random_content_size = packet_size - UDP_HEADER_SIZE - (8 * 8)
    packet_bytes = get_random_bytes(random_content_size)
    packet[64:] = packet_bytes[0:random_content_size]
    # Fill in the packet info
    return packet

def create_info_packet(sender_id, curr_time, value):
    packet = bytearray(24);
    packet_bytes = sender_id.to_bytes(8, 'big')
    packet[0:8] = packet_bytes[0:8]
    packet_bytes = curr_time.to_bytes(8, 'big')
    packet[8:16] = packet_bytes[0:8]
    packet_bytes = bytearray(struct.pack(">d", value))
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
    print("Connection terminated by id=%d, %s" % (sender_id, address));
    # Reset all of the variables
    # Close the log

# def send_packet_train(socket, sender_id, address, num_packets, packet_size, initial_send_timestamp):
#     global curr_train_num
#     seq_num = 0
#     for i in range(0, num_packets):
#         if (i == 0):
#             pkt_info = PAYLOAD_PKT_INFO_START
#         elif (i == num_packets - 1):
#             pkt_info = PAYLOAD_PKT_INFO_END
#         else:
#             pkt_info = 0

#         pkt = create_data_packet(sender_id, packet_size, seq_num, curr_train_num, pkt_info, initial_send_timestamp);
#         socket.sendto(pkt, address)
#         seq_num = seq_num + 1
#     curr_train_num += 1

def send_packet_train_thread(socket, sender_id, address, gaps, nums, packet_size, log_filename):
    global last_recv_client_packet
    global stop_sending
    global curr_train_num
    try:
        curr_train_num = 0
        while (True):
            for gap_ms in gaps:
                for num_packets in nums:
                    num_trials = int(TRAIN_SENDING_DURATION / gap_ms)
                    for a in range(0, num_trials):
                        # Sending a data train
                        now = time.time_ns();

                        if ((now - last_recv_client_packet) > 3 * 1e9):
                            print("Sending to %d has stopped due to client inactivity!" % (sender_id))
                            return 0
                        if (stop_sending):
                            print("Sending to %d has stopped because of FIN!" % (sender_id))
                            return 0  

                        seq_num = 0
                        for i in range(0, num_packets):
                            if (i == 0):
                                pkt_info = PAYLOAD_PKT_INFO_START
                            elif (i == num_packets - 1):
                                pkt_info = PAYLOAD_PKT_INFO_END
                            else:
                                pkt_info = 0
                
                            pkt = create_data_packet(sender_id, packet_size, seq_num, curr_train_num, pkt_info, num_packets, gap_ms, now);
                            socket.sendto(pkt, address)
                            seq_num = seq_num + 1
                        ##############
                        curr_train_num = curr_train_num + 1
                        time.sleep(gap_ms / float(1000))
                    time.sleep(GAP_BETWEEN_EXPERIMENT / float(1000))         

    finally:
        print("Sending to %d has stopped!" % (sender_id))
        if (log_filename != None):
            print("Server PacketTrainEchoDynamic packet trace file: %s" % log_filename)

def print_throughputs(throughputs):
    text = "["
    for i in range(0, len(throughputs)):
        text += "{:.2f}".format(throughputs[i]) + ","
    text += "]"
    return text

def main(args):
    global last_recv_client_packet
    global stop_sending

    global random_bytes

    server_ip = args[0]
    server_port = int(args[1])
    trace_folder = args[2]

    # Populate random bytes of packets
    create_random_bytes_dict(1000)

    localIP     = server_ip
    bufferSize  = 2048
    
    # Create a datagram socket
    UDPServerSocket = socket.socket(family=socket.AF_INET, type=socket.SOCK_DGRAM)

    # Bind to address and ip
    UDPServerSocket.bind((localIP, server_port))
    print("PacketTrainDynamic UDP server is up, IP=%s, port=%d ..." % (localIP, server_port))

    
    curr_sender_id = 0
    curr_gap_ms = 0
    curr_upload = 1
    curr_num_packets = 0
    curr_packet_size = 0
    curr_echo_to_first_and_tail = 0
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
    
    address = None
    address2 = None

    rtts = []

    # Listen for incoming datagrams
    while(True):
        message, address = UDPServerSocket.recvfrom(bufferSize)
        if (is_control_packet(message)):
            sender_id, content, exp_id = parse_control_packet(message)
            if (content == CONTROL_SYN):
                pkt = create_control_packet(sender_id, CONTROL_SYN_ACK)
                UDPServerSocket.sendto(pkt, address)
                
                ### WAITING FOR THE SECOND CONNECTION
                print("PacketTrainMultiple: Waiting for the second connection")
                message, address2 = UDPServerSocket.recvfrom(bufferSize)
                if (is_control_packet(message)):
                    sender_id, content, exp_id = parse_control_packet(message)
                    if (content == CONTROL_SYN):
                        pkt = create_control_packet(sender_id, CONTROL_SYN_ACK)
                        UDPServerSocket.sendto(pkt, address2)

                        #### RESET VARIABLES
                        curr_sender_id = 0
                        curr_gap_ms = 0
                        curr_upload = 1
                        curr_num_packets = 0
                        curr_packet_size = 0

                        last_sent_time = 0
                        start_sent_time = 0
                        start_sent_time_2 = 0
                        last_recv_time = 0
                        start_recv_time = 0
                        log_time = 0
                        count_packet = 0
                        is_next_second_packet = False
                        stop_sending = False
                        ########
                        throughputs = []
                        rtts = []
                        curr_log_timestamp = 0
                        curr_sender_id = sender_id

                        if (log_file is not None):
                            log_file.close()                

                        if (send_thread is not None):
                            stop_sending = True
                    else:
                        print("Unexpected content from control packet on socket2")
                        address = None
                        address2 = None
                else:
                    print("Fail to establish connection with the client!")
                    address = None
                    address2 = None
            elif (content == CONTROL_FIN):
                print("Connection terminated by id=%d, %s" % (sender_id, address))
                stop_sending = True

                #### RESET VARIABLES
                curr_sender_id = 0
                curr_gap_ms = 0
                curr_upload = 1
                curr_num_packets = 0
                curr_packet_size = 0

                last_sent_time = 0
                start_sent_time = 0
                start_sent_time_2 = 0
                last_recv_time = 0
                start_recv_time = 0
                log_time = 0
                count_packet = 0
                is_next_second_packet = False

        elif (is_config_packet(message)):
            # parse control packets
            sender_id, gap_ms, upload, num_packets, packet_size, echo_to_only_first_and_tail = parse_config_packet(message)
            if (curr_sender_id != sender_id):
                print("Got other sender_id!!")
            else:
                curr_gap_ms = gap_ms
                curr_upload = upload
                curr_num_packets = num_packets
                curr_packet_size = packet_size
                curr_echo_to_first_and_tail = echo_to_only_first_and_tail
                # create log file
                log_filename = create_log_dir(trace_folder, "server_dynamic_packet_train_trace", exp_id)
                log_file = open(log_filename, "w")
                log_file.write("%s : sender_id=%d train_gap_ms=%s upload=%d num_packets=%s size=%d echo_to_only_first_and_tail=%d\n" % (address, sender_id, curr_gap_ms, curr_upload, curr_num_packets, curr_packet_size, curr_echo_to_first_and_tail))
                log_file.write("TRAIN_NUM\tSEQ_NUM\tSEND_TIMESTAMP\tRECV_TIMESTAMP\tSENDER_ID\tPACKET_SIZE\tRTT\tTRAIN_SIZE\tTRAIN_GAP_MS\n")
                log_file.flush()
                # send ACK to client
                pkt = create_control_packet(sender_id, CONTROL_CONFIG_ACK)
                UDPServerSocket.sendto(pkt, address)
                print("%s : sender_id=%d gap_ms=%s upload=%d num_packets=%s size=%d echo_to_only_first_and_tail=%d" % (address, sender_id, curr_gap_ms, curr_upload, curr_num_packets, curr_packet_size, curr_echo_to_first_and_tail))
                # pkt = create_data_packet(sender_id, curr_packet_size)
                # UDPServerSocket.sendto(pkt, address)
                # Start sending data thread
                last_recv_client_packet = time.time_ns()
                if (curr_upload == 0):
                    stop_sending = False
                    send_thread = threading.Thread(target=send_packet_train_thread, args=(UDPServerSocket, curr_sender_id, address, curr_gap_ms, curr_num_packets, curr_packet_size, log_filename))
                    send_thread.start()
        else:
            seq_num, sent_timestamp, recv_timestamp, sender_id, train_num, pkt_info, train_size, train_gap_ms = parse_packet(message)
            now = time.time_ns();
            
            rtt = 0
            if (curr_upload == 0):
                rtt = (now - sent_timestamp) / 1e6

            #print("%d\t%d\t%d\t%d\t%d\t%d\n" % (train_num, seq_num, sent_timestamp, now, sender_id, len(message)))
            log_file.write("%d\t%d\t%d\t%d\t%d\t%d\t%.2f\t%d\t%d\n" % (train_num, seq_num, sent_timestamp, now, sender_id, len(message), rtt, train_size, train_gap_ms))
            
            if (sender_id == curr_sender_id):
                if (curr_upload == 1):
                    if (curr_echo_to_first_and_tail == 0): 
                        pkt = add_recv_time_and_trim(message, now)
                        UDPServerSocket.sendto(pkt, address2)
                    else:
                        if (pkt_info == PAYLOAD_PKT_INFO_START or pkt_info == PAYLOAD_PKT_INFO_END):
                            pkt = add_recv_time_and_trim(message, now)
                            UDPServerSocket.sendto(pkt, address2)   
                else:
                    rtts.append(rtt)

            # Logging
            if (curr_log_timestamp == 0):
                curr_log_timestamp = now
            
            if ((now - curr_log_timestamp) / 1e6 >= LOG_MS):
                avg_rtts = 0
                # log_file.flush()
                if (len(rtts) > 0):
                    avg_rtts = np.mean(rtts)
                    print("RTT - Time %d: #rtts=%d, avg_rtts=%.2f" % (log_time, len(rtts), avg_rtts))
                rtts = []
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