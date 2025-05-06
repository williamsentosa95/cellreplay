/* -*-mode:c++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */

#ifndef CELLULAR_QUEUE_HH
#define CELLULAR_QUEUE_HH

#include <string>
#include <map>
#include <random>
#include <queue>
#include <cstdint>
#include <string>
#include <fstream>
#include <memory>

#include "abstract_packet_queue.hh"
#include "file_descriptor.hh"

using namespace std;

class CellularQueue
{
private:
    const static unsigned int PACKET_SIZE = 1400; /* MAX_PACKET_SIZE */
    /*** Link queue ***/
    unsigned int next_delivery_;
    std::vector<uint64_t> heavy_pdo_schedule_;
    uint64_t link_base_timestamp_;

    std::unique_ptr<AbstractPacketQueue> packet_queue_;
    QueuedPacket packet_in_transit_;
    unsigned int packet_in_transit_bytes_left_;
    std::queue<QueuedPacket> output_queue_;
    unsigned int current_link_queue_size_;

    bool repeat_;
    bool finished_;
    bool packet_log_enabled_;

    bool uplink_;
    uint64_t start_timestamp_;

    uint64_t next_delivery_time( void ) const;

    void use_a_delivery_opportunity( void );

    void rationalize( const uint64_t now );
    void dequeue_packet( void );

    /*** Delay Queue ***/

    class DelayPDOInstance {
    public:
        uint64_t time;
        int delay;
        std::vector<int> pdo;

        DelayPDOInstance(uint64_t time_, int delay_, std::vector<int> pdo_) :
            time(time_), delay(delay_), pdo(pdo_)
         { }

    };

    std::vector<CellularQueue::DelayPDOInstance> delay_pdo_traces_;
    uint64_t delay_base_timestamp_;
    bool start_tick_;
    uint64_t end_release_time_;
    int curr_delivery_opportunity_;

    class DelayPktPairCmp final
    {
    public:
        bool operator() (const DelayQueuePacket & lhs, const DelayQueuePacket & rhs)
        {
            // NOTE: Packets are in **ascending order** of release times.
            if (lhs.release_time == rhs.release_time) {
                return lhs.sequence_number > rhs.sequence_number;
            } else {
                return lhs.release_time > rhs.release_time;    
            }
        }
    };

    std::priority_queue<DelayQueuePacket,
                        std::vector<DelayQueuePacket>,
                        DelayPktPairCmp> delay_packet_queue_;

    // Delay Short PDO states
    unsigned int current_delay_pdo_idx_;
    unsigned int current_pdo_idx_;
    // Timestamp of the pdo of the delay_pdo_traces, based on the packet interdeparture
    unsigned int current_pdo_size_;
    uint64_t pdo_base_timestamp_;
    // Initialize with -1
    int current_base_delay_;
    bool bypass_link_pdo_; 


    /******************************************/

    const std::string packet_log_path_prefix_;
    std::unique_ptr<std::ofstream> packet_logs_;
    
    uint64_t pkt_counter_;
    uint64_t last_received_packet_time_;


    /*** Drop packets **/
    double loss_rate_;
    std::bernoulli_distribution drop_dist_;
    std::default_random_engine prng_;

    vector<double> psize_latency_offset_;
    int long_to_short_timer_;
    uint64_t last_delay_queue_release_time_;

    string get_pkt_header_info(const struct iphdr *h);

    DelayQueuePacket get_next_from_delay_queue();
    void put_packet_to_link_queue( const uint64_t & now, const DelayQueuePacket & contents );
    void write_packets_from_link_queue( FileDescriptor & fd );
    unsigned int wait_time_delay_queue( void );
    unsigned int wait_time_link_queue( void );

    // Functions for getting packet delay from delay trace
    uint64_t get_pkt_release_time(const uint64_t & time, const string & contents);
    uint64_t get_pkt_release_time_from_trace(const uint64_t & time, const vector<DelayPDOInstance> & delay_pdo_trace, const unsigned int & packet_size);

    double get_psize_latency_offset(const unsigned int & pkt_size);

public:
    CellularQueue( const string & link_name, 
                   const string & packet_train_trace_filename, const string & pdo_trace_filename, 
                   const string & packet_log_path_prefix, 
                   const bool repeat, 
                   unique_ptr<AbstractPacketQueue> && packet_queue, 
                   bool uplink, uint64_t start_timestamp,
                   const double & loss_rate,
                   const string & psize_latency_offset_trace,
                   const int & long_to_short_timer,
                   const string & command_line );

    void read_packet( const std::string & contents );

    void write_packets( FileDescriptor & fd );

    unsigned int wait_time( void );

    bool pending_output( void );

    bool finished( void ) { return finished_; }
};

#endif /* CELLULAR_QUEUE_HH */
