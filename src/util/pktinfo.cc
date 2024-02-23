/* -*-mode:c++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */

#include <arpa/inet.h>
#include <iostream>
#include <netinet/ip.h>
#include <netinet/tcp.h>
#include <netinet/udp.h>
#include <system_error>

#include "pktinfo.hh"

using namespace std;

void print_src_dst_ips(const void *v)
{
  const struct iphdr *h = (struct iphdr *) v;
  if (h->version != 4) {
    return;
  }
  char ip[INET_ADDRSTRLEN];
    
  if (inet_ntop(AF_INET, &h->saddr, ip,
                INET_ADDRSTRLEN) == NULL) {
    throw runtime_error("Invalid IPv4 source address!");
  }
  cerr << "#- " << ip;
    
  if (inet_ntop(AF_INET, &h->daddr, ip,
                INET_ADDRSTRLEN) == NULL) {
    throw runtime_error("Invalid IPv4 source address!");
  }
  cerr << " => " << ip << endl;
}

void print_tcp_endpoints(const void *v, const uint64_t& delay)
{
  const char *b = (const char *) v;
  const struct iphdr *h = (struct iphdr *) b;
  if (h->version != 4) {
    return;
  }

  const struct tcphdr *tcph = (struct tcphdr*) (b + h->ihl*4);
  char ip[INET_ADDRSTRLEN];
    
  if (inet_ntop(AF_INET, &h->saddr, ip,
                INET_ADDRSTRLEN) == NULL) {
    throw runtime_error("Invalid IPv4 source address!");
  }
  cerr << "#- " << ip << ":" << ntohs(tcph->th_sport);
    
  if (inet_ntop(AF_INET, &h->daddr, ip,
                INET_ADDRSTRLEN) == NULL) {
    throw runtime_error("Invalid IPv4 source address!");
  }
  cerr << " => " << ip << ":" << ntohs(tcph->th_dport)
       << "  " << delay << " ms" << endl;
}

void print_udp_endpoints(const void *v, const uint64_t& delay)
{
  const char *b = (const char *) v;
  const struct iphdr *h = (struct iphdr *) b;
  if (h->version != 4) {
    return;
  }

  const struct udphdr *udph = (struct udphdr*) (b + h->ihl*4);
  char ip[INET_ADDRSTRLEN];
    
  if (inet_ntop(AF_INET, &h->saddr, ip,
                INET_ADDRSTRLEN) == NULL) {
    throw runtime_error("Invalid IPv4 source address!");
  }
  cerr << "#- " << ip << ":" << ntohs(udph->uh_sport);
    
  if (inet_ntop(AF_INET, &h->daddr, ip,
                INET_ADDRSTRLEN) == NULL) {
    throw runtime_error("Invalid IPv4 source address!");
  }
  cerr << " => " << ip << ":" << ntohs(udph->uh_dport)
       << "  " << delay << " ms" << endl;
}
