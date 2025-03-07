# CellReplay

The technical paper for CellReplay will appear at USENIX NSDI'25.

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

## Description

CellReplay is a record-and-replay emulator for cellular network. CellReplay can replay a set of pre-recorded network traces through an emulated TUN interface implemented via a shell. Any unmodified application can then be run inside the CellReplay shell and result in a similar performance and behavior as it was running on the live cellular network. 
We provided 5G T-Mobile and Verizon traces recorded under multiple scenarios that includes stationary, walking, and driving. 

## Usage

To use the cellreplay emulator, you need to call `mm-cellular`.
```sh
$ mm-cellular NUM_ARGS UP-PACKET-TRAIN-TRACE DOWN-PACKET-TRAIN-TRACE UP-PDO DOWN-PDO PACKET-LOG-PATH-PREFIX [OPTION]... [COMMAND]
```

NUM_ARGS = the total number of arguments (including the mm-cellular command itself) \
UP-PACKET-TRAIN-TRACE = the uplink base delay and light PDO trace \
DOWN-PACKET-TRAIN-TRACE = the downlink base delay and light PDO trace \
UP-PDO = the uplink heavy PDO trace \
DOWN-PDO = the downlink heavy PDO trace \
PACKET-LOG-PATH-PREFIX = Folder path for the log purposes \
[OPTION] = optional commands

This is an example of the command with two optional arguments (for latency compensation based on the packet size) and packet log directory (which should exist beforehand) of `$HOME/packet-logs`. Note that the traces have to be decompressed first. 

```sh
mm-cellular 9 traces/tmobile/driving/up-delay-light-pdo traces/tmobile/driving/down-delay-light-pdo \
traces/tmobile/driving/up-pdo traces/tmobile/driving/down-pdo \
$HOME/packet-logs \
--psize-latency-offset-up=traces/tmobile/driving/latency-offset-up \
--psize-latency-offset-down=traces/tmobile/driving/latency-offset-down
```

An unmodified application can run inside the `mm-cellular` shell and have all their incoming (downlink) and outgoing (uplink) packets delayed as it is going to the real cellular network.

## Citation

If you use CellReplay for your research, we will appreciate if you cite it.

@inproceedings {cellreplay-nsdi25,
	title = {{CellReplay}: Towards accurate record-and-replay for cellular networks},
	booktitle = {22nd USENIX Symposium on Networked Systems Design and Implementation (NSDI 25)},
	year = {2025},
	address = {Philadelphia, PA},
	url = {https://www.usenix.org/conference/nsdi25/presentation/sentosa},
	publisher = {USENIX Association},
	month = apr
}