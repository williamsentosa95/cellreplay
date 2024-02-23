/* -*-mode:c++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */

#ifndef PKTINFO_HH
#define PKTINFO_HH

// Print source and destination IP addresses.
void print_src_dst_ips(const void *v);

// Print endpoints of a TCP connection to STDERR.
void print_tcp_endpoints(const void *v, const uint64_t& delay);

// Print endpoints of a UDP connection to STDERR.
void print_udp_endpoints(const void *v, const uint64_t& delay);

#endif /* PKTINFO_HH */
