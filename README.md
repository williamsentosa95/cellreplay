# CellReplay

The technical paper for CellReplay is published and presented at NSDI'25 and is available [here](https://www.usenix.org/conference/nsdi25/presentation/sentosa).

## Description

CellReplay is a record-and-replay emulator for cellular network emulation. CellReplay can replay a set of pre-recorded network traces through an emulated TUN interface implemented in a [Mahimahi shell](http://mahimahi.mit.edu/). Any unmodified application can then be run inside the CellReplay shell and its network traffic will be emulated as it was going through a live cellular network.
CellReplay will be useful if you want to evaluate your networked applications (e.g., video streaming, web browsing, congestion control, etc) in emulated cellular network conditions. We provided pre-recorded 5G T-Mobile and Verizon traces recorded under multiple scenarios that includes stationary, walking, and driving. 


## Installation
Install all the required dependencies using this command.

```sh
$ sudo apt-get install protobuf-compiler libprotobuf-dev autotools-dev dh-autoreconf iptables pkg-config dnsmasq-base debhelper libxcb-present-dev libcairo2-dev libpango1.0-dev libtar-dev 
```

Install CellReplay using this command.

```sh
$ cd cellreplay
$ ./autogen.sh
$ ./configure
$ make
$ sudo make install
```

The network traces are located in the "traces" folder, and it has to be decompressed before it can be used.  

```sh
$ unzip traces/cellular-traces.zip -d traces/
```

## Usage

To use the cellreplay emulator, you need to call `mm-cellular`.
```sh
$ mm-cellular NUM_ARGS UP-PACKET-TRAIN-TRACE DOWN-PACKET-TRAIN-TRACE UP-PDO DOWN-PDO [OPTION]... [COMMAND]
```

NUM_ARGS = the total number of arguments (including the mm-cellular command itself) \
UP-PACKET-TRAIN-TRACE = the uplink base delay and light PDO trace \
DOWN-PACKET-TRAIN-TRACE = the downlink base delay and light PDO trace \
UP-PDO = the uplink heavy PDO trace \
DOWN-PDO = the downlink heavy PDO trace \
[OPTION] = optional commands

This is an example command to run tmobile driving traces with one optional arguments (for latency compensation based on the packet size). Note that the traces have to be decompressed first. 

```sh
mm-cellular 8 traces/tmobile/driving/up-delay-light-pdo traces/tmobile/driving/down-delay-light-pdo \
traces/tmobile/driving/up-heavy-pdo traces/tmobile/driving/down-heavy-pdo \
--psize-latency-offset-up=traces/tmobile/driving/latency-offset-up \
--psize-latency-offset-down=traces/tmobile/driving/latency-offset-down
```

An unmodified application can run inside the `mm-cellular` shell and have all their incoming (downlink) and outgoing (uplink) packets delayed as it is going to the real cellular network.

## Use case example: running IPerf3 in CellReplay shell

You need to install IPerf3 first if you haven't had it. First, open a terminal and start the iperf3 server process by running 

```sh
iperf3 -s
```

In a separate terminal window, run `mm-cellular` command with all the required arguments. This will spawn `[cell-link]` shell and also show the `egress_addr` information. In my case, the egress_addr is `100.64.0.1:0`. Then, you can run the iperf3 client inside the shell, with the `egress_addr` IP as the server IP. For example, my iperf3 command will be

```sh
iperf3 -c 100.64.0.1
```

All of the iperf3 client's network traffic (IP packets) will be delayed according to the traces that you choose (i.e., driving with tmobile if you use the previous command).

## Citation

If you use CellReplay for your research, we will appreciate if you cite it.

```sh
@inproceedings {cellreplay-nsdi25,
	title = {{CellReplay}: Towards accurate record-and-replay for cellular networks},
    author= {William Sentosa and Balakrishnan Chandrasekaran and P. Brighten Godfrey and Haitham Hassanieh},
	booktitle = {22nd USENIX Symposium on Networked Systems Design and Implementation (NSDI 25)},
	year = {2025},
	address = {Philadelphia, PA},
	url = {https://www.usenix.org/conference/nsdi25/presentation/sentosa},
	publisher = {USENIX Association},
	month = apr
}
```

