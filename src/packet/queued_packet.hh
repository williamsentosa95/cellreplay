/* -*-mode:c++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */

#ifndef QUEUED_PACKET_HH
#define QUEUED_PACKET_HH

#include <string>

struct DelayQueuePacket
{
    uint64_t arrival_time;
    uint64_t sequence_number;
    uint64_t release_time;
    std::string contents;
    std::string ip_port_info;
    bool is_bypass_link_queue;

    DelayQueuePacket( const std::string & s_contents, uint64_t s_arrival_time )
        : arrival_time( s_arrival_time ),  
          sequence_number( 0 ), 
          release_time( s_arrival_time ),
          contents( s_contents ),
          ip_port_info( "" ),
          is_bypass_link_queue( true )
    {}

    DelayQueuePacket( const std::string & s_contents, 
                      const std::string & s_ip_port_info,
                      const uint64_t & s_arrival_time, 
                      const uint64_t & s_sequence_number,
                      const uint64_t & s_release_time,
                      const bool & s_is_bypass_link_queue) 
        : arrival_time( s_arrival_time ),  
          sequence_number( s_sequence_number ), 
          release_time( s_release_time ),
          contents( s_contents ),
          ip_port_info( s_ip_port_info ),
          is_bypass_link_queue( s_is_bypass_link_queue )
    {}

};


struct QueuedPacket
{
    uint64_t initial_arrival_time; // Arrival time in the first queue, including delay queue
    uint64_t arrival_time; // Arrival time to the link queue
    uint64_t sequence_number;
    uint64_t release_time_from_delay_queue;
    std::string contents;
    std::string ip_port_info;

    // Attributes for DChannel
    std::string pkt_key;
    bool is_llc;

    QueuedPacket( const std::string & s_contents, uint64_t s_arrival_time )
        : initial_arrival_time( s_arrival_time ),
          arrival_time( s_arrival_time ),  
          sequence_number( 0 ), 
          release_time_from_delay_queue( s_arrival_time ),
          contents( s_contents ),
          ip_port_info( "" ),
          pkt_key(""),
          is_llc( false )
    {}

    // For DChannel -- adv_delay_link_rrc_queue
    QueuedPacket( const std::string & s_contents, 
                  const uint64_t & s_arrival_time, 
                  const uint64_t & s_sequence_number, 
                  const std::string & s_pkt_key,
                  const bool & s_is_llc)
        : initial_arrival_time( s_arrival_time ),
          arrival_time( s_arrival_time ),  
          sequence_number( s_sequence_number ), 
          release_time_from_delay_queue( s_arrival_time ),
          contents( s_contents ),
          ip_port_info( s_pkt_key ),
          pkt_key( s_pkt_key ),
          is_llc( s_is_llc )
    {}


    // Release time from delay queue is similar to the s_arrival_time..
    QueuedPacket( const std::string & s_contents, 
                      const std::string & s_ip_port_info,
                      const uint64_t & s_initial_arrival_time,
                      const uint64_t & s_arrival_time,
                      const uint64_t & s_sequence_number,
                      const uint64_t & s_release_time_from_delay_queue)
        : initial_arrival_time( s_initial_arrival_time ),
          arrival_time( s_arrival_time ),  
          sequence_number( s_sequence_number ), 
          release_time_from_delay_queue( s_release_time_from_delay_queue ),
          contents( s_contents ),
          ip_port_info( s_ip_port_info ),
          pkt_key( "" ),
          is_llc( false )   
    {}
};

#endif /* QUEUED_PACKET_HH */
