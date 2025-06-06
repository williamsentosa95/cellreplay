/* -*-mode:c++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */

#include <limits>
#include <cassert>
#include <arpa/inet.h>
#include <fstream>
#include <iostream>
#include <limits>
#include <net/ethernet.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <netinet/udp.h>
#include <sstream>
#include <string>
#include <bitset>
#include <string.h>

#include "cellular_queue.hh"
#include "timestamp.hh"
#include "util.hh"
#include "ezio.hh"
#include "abstract_packet_queue.hh"

using namespace std;

CellularQueue::CellularQueue( const string & link_name, 
               const string & packet_train_trace_filename, const string & heavy_pdo_trace_filename, 
               const string & packet_log_path_prefix, 
               const bool repeat, 
               unique_ptr<AbstractPacketQueue> && packet_queue, 
               bool uplink, uint64_t start_timestamp,
               const double & loss_rate, 
               const string & psize_latency_offset_trace,
               const int & long_to_short_timer,
               const string & command_line ) 
    : next_delivery_( 0 ),
      heavy_pdo_schedule_(),
      link_base_timestamp_( timestamp() ),
      packet_queue_( move( packet_queue ) ),
      packet_in_transit_( "", 0 ),
      packet_in_transit_bytes_left_( 0 ),
      output_queue_(),
      current_link_queue_size_(0),
      repeat_( repeat ),
      finished_( false ),
      packet_log_enabled_( false ),
      uplink_( uplink ),
      start_timestamp_(start_timestamp),
      delay_pdo_traces_(),
      delay_base_timestamp_(timestamp()),
      start_tick_(false),
      end_release_time_( 0 ),
      curr_delivery_opportunity_( 1400 ),
      delay_packet_queue_(),
      current_delay_pdo_idx_(),
      current_pdo_idx_(),
      current_pdo_size_( 1 ),
      pdo_base_timestamp_( 0 ),
      current_base_delay_( -1 ),
      bypass_link_pdo_(true),
      packet_log_path_prefix_(packet_log_path_prefix),
      packet_logs_(),
      pkt_counter_(0),
      last_received_packet_time_(0),
      loss_rate_(loss_rate),
      drop_dist_(loss_rate),
      prng_(random_device()()),
      psize_latency_offset_(),
      long_to_short_timer_(long_to_short_timer),
      last_delay_queue_release_time_(0)
{
    assert_not_root();

    /* open PDO filename and load PDO schedule */
    ifstream heavy_pdo_trace_file( heavy_pdo_trace_filename );

    if ( not heavy_pdo_trace_file.good() ) {
        throw runtime_error( heavy_pdo_trace_filename + ": error opening for reading" );
    }

    string line;
    /* Read heavy PDO trace */
    while ( heavy_pdo_trace_file.good() and getline( heavy_pdo_trace_file, line ) ) {
        if ( line.empty() ) {
            throw runtime_error( heavy_pdo_trace_filename + ": invalid empty line" );
        }

        const uint64_t ms = myatoi( line );

        if (ms >= start_timestamp_) {
            if (! heavy_pdo_schedule_.empty()) {
                if ( (ms - start_timestamp_) < heavy_pdo_schedule_.back() ) {
                    throw runtime_error( heavy_pdo_trace_filename + ": timestamps must be monotonically nondecreasing" );
                } 
            }

            uint64_t time = ms - start_timestamp_;
            heavy_pdo_schedule_.emplace_back( time );
        }
    }

    if ( heavy_pdo_schedule_.empty() ) {
        throw runtime_error( heavy_pdo_trace_filename + ": no valid timestamps found" );
    }

    if ( heavy_pdo_schedule_.back() == 0 ) {
        throw runtime_error( heavy_pdo_trace_filename + ": trace must last for a nonzero amount of time" );
    }


    /*** Read delay and light_pdo trace ***/
    ifstream packet_train_trace_file( packet_train_trace_filename );

    if ( not packet_train_trace_file.good() ) {
        throw runtime_error( packet_train_trace_filename + ": error opening for reading" );
    }

    while ( packet_train_trace_file.good() and getline( packet_train_trace_file, line ) ) {
        if ( line.empty() ) {
            throw runtime_error( packet_train_trace_filename + ": invalid empty line" );
        }

        istringstream iss (line);
        string word;
        int col = 0;

        uint64_t time_ms =  0;
        int first_packet_delay_ms = -1;
        vector<int> packet_delivery_oportunity;

        while (getline(iss, word, ' ')) {
            if (col == 0) { // First column is timestamp
                time_ms = myatoi(word);
            } else if (col == 1) { // Second column is the base delay
                first_packet_delay_ms = myatoi(word);
            } else { // The rest is the light PDOs
                int pdo = myatoi(word);
                packet_delivery_oportunity.push_back(pdo);
            }
            col++;
        }

        assert(first_packet_delay_ms >= 0);

        if (time_ms >= start_timestamp_) {
            if ( ! delay_pdo_traces_.empty() ) {
                if ( (time_ms - start_timestamp_) < delay_pdo_traces_.back().time ) {
                    throw runtime_error( packet_train_trace_filename + ": timestamps must be monotonically nondecreasing" );
                }
            }

            uint64_t time = time_ms - start_timestamp_;
            DelayPDOInstance instance(time, first_packet_delay_ms, packet_delivery_oportunity);
            delay_pdo_traces_.push_back( instance );
        }
    }

    if (delay_pdo_traces_.size() <= 2) {
        throw runtime_error( packet_train_trace_filename + ": there should be at least three lines" );
    }

    /* open packet logfile */
    if (packet_log_path_prefix_ != "") {
        string packet_log_filename = packet_log_path_prefix_ + "/packet-log-" + (uplink_ ? "uplink" : "downlink");
        packet_logs_.reset( new ofstream( packet_log_filename ) );
        if (not packet_logs_->good()) {
            throw runtime_error( packet_log_filename + ": error opening for writing" );
        }
        *packet_logs_ << "Packet train trace filename=" << packet_train_trace_filename << endl;
        *packet_logs_ << "Link filename=" << heavy_pdo_trace_filename << endl;
        // SEQ_NUM, ip_port, arrival_time, release_time, total_delay, bypass_link_queue, heavyPDO_queue_delay 
        *packet_logs_ << "Seq_num \t packet_ip_port \t arrival_time_ms \t relase_time_ms \t total_delay_ms \t is_lightPDO_only \t heavyPDO_queue_delay" << endl;
        
        packet_log_enabled_ = true;
    }

    /*** Read packet size latency offset ***/
    if (psize_latency_offset_trace != "") {
        ifstream offset_file(psize_latency_offset_trace);
        if ( not offset_file.good() ) {
            throw runtime_error( psize_latency_offset_trace + ": error opening for reading" );
        }
        // Now, we always assume that the file size is from 0..1400
        int curr_size = 100;
        while ( offset_file.good() and getline( offset_file, line ) ) {
            istringstream iss (line);
            string word;
            int num = 0;

            while (getline(iss, word, ' ')) {
                if (num == 0) {
                    int psize = myatoi(word);
                    if (psize != curr_size) {
                        throw runtime_error( psize_latency_offset_trace + ": packet size latency offset should be an increment of 100 bytes and start from 100 bytes");
                    } else {
                        curr_size += 100;
                    }
                } else if (num == 1) {
                    double offset = myatof(word);
                    psize_latency_offset_.push_back(offset);
                }
                num++;
            }
        }
        offset_file.close();
    }

}

uint64_t CellularQueue::next_delivery_time( void ) const
{
    if ( finished_ ) {
        return -1;
    } else {
        return heavy_pdo_schedule_.at( next_delivery_ ) + link_base_timestamp_;
    }
}

void CellularQueue::use_a_delivery_opportunity( void )
{
    next_delivery_ = (next_delivery_ + 1) % heavy_pdo_schedule_.size();

    /* wraparound */
    if ( next_delivery_ == 0 ) {
        if ( repeat_ ) {
            link_base_timestamp_ += heavy_pdo_schedule_.back();
        } else {
            finished_ = true;
        }
    }
}

/* emulate the link up to the given timestamp */
/* this function should be called before enqueueing any packets and before
   calculating the wait_time until the next event */
void CellularQueue::rationalize( const uint64_t now )
{
    while ( next_delivery_time() <= now ) {
        const uint64_t this_delivery_time = next_delivery_time();

        /* burn a delivery opportunity */
        unsigned int bytes_left_in_this_delivery = PACKET_SIZE;
        use_a_delivery_opportunity();

        while ( bytes_left_in_this_delivery > 0 ) {
            if ( not packet_in_transit_bytes_left_ ) {
                if ( packet_queue_->empty() ) {
                    break;
                }
                packet_in_transit_ = packet_queue_->dequeue();
                packet_in_transit_bytes_left_ = packet_in_transit_.contents.size();
            }

            assert( packet_in_transit_.arrival_time <= this_delivery_time );
            assert( packet_in_transit_bytes_left_ <= PACKET_SIZE );
            assert( packet_in_transit_bytes_left_ > 0 );
            assert( packet_in_transit_bytes_left_ <= packet_in_transit_.contents.size() );

            /* how many bytes of the delivery opportunity can we use? */
            const unsigned int amount_to_send = min( bytes_left_in_this_delivery,
                                                     packet_in_transit_bytes_left_ );

            /* send that many bytes */
            packet_in_transit_bytes_left_ -= amount_to_send;
            bytes_left_in_this_delivery -= amount_to_send;

            /* has the packet been fully sent? */
            if ( packet_in_transit_bytes_left_ == 0 ) {
                /* this packet is ready to go */
                // output_queue_.push( move( packet_in_transit_.contents ) ); 
                output_queue_.push( packet_in_transit_ );  
            }
        }
    }
}

DelayQueuePacket CellularQueue::get_next_from_delay_queue()
{
    if ( (!delay_packet_queue_.empty())
            && (delay_packet_queue_.top().release_time <= timestamp()) ) {
        auto ret = delay_packet_queue_.top();
        delay_packet_queue_.pop();
        return ret;
    } else {
        return DelayQueuePacket("", 0);
    }
}

void CellularQueue::put_packet_to_link_queue( const uint64_t & now, const DelayQueuePacket & packet )
{
    if ( packet.contents.size() > PACKET_SIZE ) {
        throw runtime_error( "packet size is greater than maximum" );
    }

    rationalize( now );
    packet_queue_->enqueue( QueuedPacket( packet.contents, packet.ip_port_info, packet.arrival_time, now, packet.sequence_number, packet.release_time ) );
    current_link_queue_size_ += 1;
  
}

void CellularQueue::write_packets_from_link_queue( FileDescriptor & fd )
{
    uint64_t now = timestamp();
    while (not output_queue_.empty()) {
        /* Consume the output_queue */
        QueuedPacket packet = output_queue_.front();
        fd.write( packet.contents );
        output_queue_.pop();

        assert(current_link_queue_size_ > 0);
        current_link_queue_size_ = current_link_queue_size_ - 1;

        // Print output log
        int total_delay = now - packet.initial_arrival_time;   
        int link_queue_delay = now - packet.arrival_time;
        if (packet_log_enabled_) {
            // SEQ_NUM, ip_port, arrival_time, release_time, total_delay, bypass_link_queue, heavyPDO_queue_delay   
            *packet_logs_ << packet.sequence_number << "\t" << packet.ip_port_info << "\t" << packet.initial_arrival_time  << "\t" << now << "\t" << total_delay << "\t" << "false" << "\t" << link_queue_delay << endl; 
        }
    }
}


/**
 * This function (1) move packets from delay queue (base delay + light PDO queue) to link queue (heavy PDO queue) 
 * and (2) write packets from link_queue to the endpoint file descriptor.
 */  
void CellularQueue::write_packets( FileDescriptor & fd ) {
    /* Move packets from delay_queue to link_queue */
    DelayQueuePacket next_packet = get_next_from_delay_queue();
    uint64_t now = timestamp();
    while ( not next_packet.contents.empty() ) {      
        if (next_packet.is_bypass_link_queue && current_link_queue_size_ == 0) {     
            fd.write( next_packet.contents );
            int total_delay = now - next_packet.arrival_time;
            if (packet_log_enabled_) {
                // SEQ_NUM, ip_port, arrival_time, release_time, total_delay, bypass_link_queue, heavyPDO_queue_delay   
                *packet_logs_ << next_packet.sequence_number << "\t" << next_packet.ip_port_info << "\t" << next_packet.arrival_time  << "\t" << now << "\t" << total_delay << "\t" << "true" << "\t" << "0" << endl; 
            }
        } else {
            put_packet_to_link_queue( now, next_packet );    
        }
        next_packet = get_next_from_delay_queue();
    }

    /* Write out packets from link_queue into fd (if any) */
    write_packets_from_link_queue( fd );
}


unsigned int CellularQueue::wait_time_delay_queue( void )
{
    if ( delay_packet_queue_.empty() ) {
        return numeric_limits<uint16_t>::max();
    }

    const auto now = timestamp();

    if ( delay_packet_queue_.top().release_time <= now ) {
        return 0;
    } else {
        return delay_packet_queue_.top().release_time - now;
    }
}

unsigned int CellularQueue::wait_time_link_queue( void )
{
    const auto now = timestamp();

    rationalize( now );

    int wait_time = next_delivery_time() - now;

    if (wait_time < 0) {
        wait_time = 0;
    }

    return wait_time;
}

unsigned int CellularQueue::wait_time( void )
{
    unsigned int wait_time_delay = wait_time_delay_queue();
    unsigned int wait_time_link = wait_time_link_queue();
    return min(wait_time_delay, wait_time_link);
}

bool CellularQueue::pending_output( void ) {
    return (not output_queue_.empty()) or (wait_time_delay_queue() <= 0);
}


// Get the packet release time from base delay + light PDOs.
// Get a new base delay and light PDOs if start_new_short_pdo_trace is true
// bypass_link_pdo_ is a flag that control whether this packet should skip heavy PDO queue or not.
// A packet should skip heavy PDO queue if it still belongs to the light PDO. 
uint64_t CellularQueue::get_pkt_release_time_from_trace(const uint64_t & time, const vector<DelayPDOInstance> & delay_pdo_trace, const unsigned int & packet_size) {
    int64_t relative_timestamp_ms = (time - delay_base_timestamp_);
    uint64_t delay_ms = 0;
    uint64_t release_time = time;
    int64_t time_gap_from_base_pdo = relative_timestamp_ms - pdo_base_timestamp_;

    int branch = -1;
    
    // Start a new short pdo trace when the queue is empty for long_to_short_timer_
    // Or when the system start (current_base_delay_  < 0)
    bool start_new_short_pdo_trace = (current_base_delay_ < 0) || (time + current_base_delay_ > last_delay_queue_release_time_ + long_to_short_timer_);

    if (start_new_short_pdo_trace) {

        // Search for the correct instance
        while ( delay_pdo_trace[current_delay_pdo_idx_ + 1].time <= relative_timestamp_ms ) {
            current_delay_pdo_idx_ += 1;
            // Repeat the trace if we already exceed the trace length
            if (current_delay_pdo_idx_ >= (delay_pdo_trace.size() - 1)) {
                // Reset the trace
                delay_base_timestamp_ = delay_base_timestamp_ + delay_pdo_trace[current_delay_pdo_idx_].time;
                relative_timestamp_ms = time - delay_base_timestamp_;
                current_delay_pdo_idx_ = 0;
            }
            
        }

        current_pdo_size_ = delay_pdo_trace[current_delay_pdo_idx_].pdo.size();
        
        // Interpolate the base delay through linear regression
        float ratio = (relative_timestamp_ms - delay_pdo_trace[current_delay_pdo_idx_].time) / float(delay_pdo_trace[current_delay_pdo_idx_ + 1].time - delay_pdo_trace[current_delay_pdo_idx_].time);
        float adjusted_delay = ratio * float(delay_pdo_trace[current_delay_pdo_idx_ + 1].delay - delay_pdo_trace[current_delay_pdo_idx_].delay);
        
        // This is the base delay for the packet within this PDO instance
        delay_ms = int(delay_pdo_trace[current_delay_pdo_idx_].delay + adjusted_delay);
        current_base_delay_ = delay_ms;

        release_time = time + delay_ms;
        bypass_link_pdo_ = true;
        pdo_base_timestamp_ = relative_timestamp_ms;
        current_pdo_idx_ = 0;
        curr_delivery_opportunity_ = PACKET_SIZE - packet_size;
        branch = 1;
    } else {
        // Use the current Short PDO
        int64_t timestamp_from_pdo_base = relative_timestamp_ms - pdo_base_timestamp_;

        // Find the correct PDO time
        while (current_pdo_idx_ < current_pdo_size_ && timestamp_from_pdo_base > delay_pdo_trace[current_delay_pdo_idx_].pdo[current_pdo_idx_]) {
            current_pdo_idx_ += 1;
            curr_delivery_opportunity_ = PACKET_SIZE;
        }    

        if (curr_delivery_opportunity_ < packet_size) {
            current_pdo_idx_ += 1;
            curr_delivery_opportunity_ = PACKET_SIZE;
        }

        // Burn a delivery opportunity
        curr_delivery_opportunity_ = curr_delivery_opportunity_ - packet_size;
        
        if (current_pdo_idx_ >= current_pdo_size_) { // This packet uses to light PDO
            bypass_link_pdo_ = false;
            release_time = max(time + current_base_delay_,  time + delay_pdo_trace[current_delay_pdo_idx_].pdo[current_pdo_size_ - 1]);
            branch = 2;
        } else { // This packet already exceed the light PDO and it should use heavy PDO
            assert(delay_pdo_trace[current_delay_pdo_idx_].pdo[current_pdo_idx_] >= timestamp_from_pdo_base);
            release_time = time + current_base_delay_ + (delay_pdo_trace[current_delay_pdo_idx_].pdo[current_pdo_idx_] - timestamp_from_pdo_base);
            bypass_link_pdo_ = true; 
            branch = 3;
        }
    }   
    last_delay_queue_release_time_ = release_time; 
    return release_time;
}

uint64_t CellularQueue::get_pkt_release_time(const uint64_t & time, const string & contents) {
    uint64_t release_time = get_pkt_release_time_from_trace(time, delay_pdo_traces_, contents.size());
    return release_time;
}

string CellularQueue::get_pkt_header_info(const struct iphdr *h) {
    // FIXME: Add support for IPv6.
    char src_ip[INET_ADDRSTRLEN];
    char dst_ip[INET_ADDRSTRLEN];

    if (inet_ntop(AF_INET, &(h->saddr) , src_ip,
                  INET_ADDRSTRLEN) == NULL) {
        throw runtime_error("Invalid IP address encountered!");
    }

    if (inet_ntop(AF_INET, &(h->daddr) , dst_ip,
                  INET_ADDRSTRLEN) == NULL) {
        throw runtime_error("Invalid IP address encountered!");
    }

    const char *pkt = (const char *) h;
    string protocol = "OTHER";

    stringstream ss;

    switch (h->protocol) {
        case IPPROTO_ICMP: {
            protocol = "ICMP";
            ss << protocol << "\t" << src_ip << "\t" << dst_ip;
            break;
        }
        case IPPROTO_TCP: {
            protocol = "TCP";
            const struct tcphdr *tcph = (struct tcphdr*) (pkt + h->ihl*4);
            uint16_t s_port = ntohs(tcph->th_sport);
            uint16_t d_port = ntohs(tcph->th_dport);
            ss << protocol << "\t" << src_ip << ":" << s_port << "\t" << dst_ip << ":" << d_port; 
            break;
        }
        case IPPROTO_UDP: {
            protocol = "UDP";
            const struct udphdr *udph = (struct udphdr*) (pkt + h->ihl*4);
            uint16_t s_port = ntohs(udph->source);
            uint16_t d_port = ntohs(udph->dest);
            ss << protocol << "\t" << src_ip << ":" << s_port << "\t" << dst_ip << ":" << d_port;
            break;
        }
        default: {
            protocol = "OTHER";
            ss << protocol << "\t" << src_ip << "\t" << dst_ip;
            break;
        }
    }
    return ss.str();
}

double CellularQueue::get_psize_latency_offset(const unsigned int & psize) {
    double result = 0;

    if (psize_latency_offset_.size() == 0) {
        result = 0;
    } else {
        int idx = (psize / 100) - 1;
        int remainder = psize % 100;
        if (idx < 0) { // For psize < 100, the offset will be based on 100 bytes
            result = psize_latency_offset_[0];
        } else if (idx >= psize_latency_offset_.size() - 1) {
            result = psize_latency_offset_.back();
        } else {
            result = psize_latency_offset_[idx] + ((remainder / 100.0) * (psize_latency_offset_[idx + 1] - psize_latency_offset_[idx]));
        }
    }
    return result;
}

void CellularQueue::read_packet( const string & contents )
{
    if (contents.size() < sizeof(struct iphdr)) {
        throw new runtime_error("Packet is too small!");
    }
    
    if (contents.size() > PACKET_SIZE) {
        throw new runtime_error("Packet size (" + to_string(contents.size()) + ")  exceeds the maximum allowed (" + to_string(PACKET_SIZE) + ")" ); 
    }

    uint64_t now = timestamp();
    
    const char* pkt = contents.data();
    const struct iphdr *h = (struct iphdr *)(pkt + 4);
    string ip_port_info = get_pkt_header_info(h);

    if (!start_tick_) {
        // The trace is started
        start_tick_ = true;        
        link_base_timestamp_ = now;
        delay_base_timestamp_ =  now;
    }

    // Packet release time from base delay + light PDOs
    uint64_t release_time = get_pkt_release_time(now, contents);

    // Apply latency offset adjustment based on psize, but apply this only if it does not cause out-of-order
    int latency_offset = int(round(get_psize_latency_offset(contents.size())));
    if (release_time + latency_offset >= end_release_time_) {
        release_time = release_time + latency_offset;
    }

    if (release_time < end_release_time_) {
        release_time = end_release_time_;
        bypass_link_pdo_ = false;
    }

    assert(release_time >= end_release_time_);
    end_release_time_ = release_time;
    uint64_t delay_ms = release_time - now;

    delay_packet_queue_.emplace(DelayQueuePacket(contents, ip_port_info, now, pkt_counter_, release_time, bypass_link_pdo_));

    if (packet_log_enabled_) {
        *packet_logs_ << pkt_counter_ << "\t" << now << "\t" << get_pkt_header_info(h) << "\t" << contents.size() << "\t" << delay_ms << "\t" << (bypass_link_pdo_ ? "true" : "false") << endl;
    }
    
    pkt_counter_++;

}

