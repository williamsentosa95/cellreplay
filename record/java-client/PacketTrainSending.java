import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;

import packets.*;

public class PacketTrainSending {

	public class TrainTuple {
		public long start_time;
		public int packet_count;
	}
	
	private DatagramSocket socket;
	private InetAddress serverAddr;

	private String serverIP;
	private int serverPort;
	private int packetSize;
	private long senderId;

	private long uplinkGapMs;
	private long downlinkGapMs;
	private long downloadNumPackets;
	private long uploadNumPackets;
	
	private long trainNum;
	private long currLogTimestamp;
	private long logTime;

	private ArrayList<Double> throughputs;
	private ArrayList<Double> rtts;
	private ArrayList<Double> tps;
	private ArrayList<Double> up_tps;
	private ArrayList<Double> overall_rtts;
	
	private Hashtable<Long, TrainTuple> train_tables;

	final public static int CONN_ESTABLISHED = 400;
	final public static int START_SENDING = 500;
	final public static int FOREIGN_PACKET = 600;
	final public static int FAILED_CONNECTION = 700;
	final private int LOG_MS = 1000;
	
	final private int CLIENT_PORT = 7002; 

	private FileLoggingBuffered packetLogFileLogging;
	private FileLoggingBuffered throughputLogFileLogging;
	private FileLoggingBuffered rttLogFileLogging;
	
	private RandomBytes randomBytes;
	
	private long experimentId = 0;
	private String traceDir;
	private String expName;
	private String expCondition = "";

	public PacketTrainSending(long senderId, String serverIP, String clientIP, int serverPort, int packetSize, String traceDir, String expName) throws IOException {
		this.senderId = senderId;
		this.serverIP = serverIP;
		this.serverPort = serverPort;
		this.packetSize = packetSize;
		this.traceDir = traceDir;
		this.serverAddr = InetAddress.getByName(serverIP);
		this.socket = new DatagramSocket(CLIENT_PORT, InetAddress.getByName(clientIP));
		this.socket.setSoTimeout(2000);
		this.throughputs = new ArrayList<Double>();
		this.rtts = new ArrayList<Double>();
		this.tps = new ArrayList<Double>();
		this.up_tps = new ArrayList<Double>();
		this.overall_rtts = new ArrayList<Double>();
		this.randomBytes = new RandomBytes(1000);
		this.train_tables = new Hashtable<>();
		this.traceDir = traceDir;
		this.expName = expName;
	}
	
	public void setExperimentId(long id) {
		this.experimentId = id;
	}
	
	public void setExperimentCondition(String cond) {
    	this.expCondition = cond;
    }
	
	private void setupLog() throws IOException {
		this.packetLogFileLogging = new FileLoggingBuffered(traceDir, expName, "train", experimentId, senderId, expCondition);
		this.throughputLogFileLogging = new FileLoggingBuffered(traceDir, expName, "throughput", experimentId, senderId, expCondition);
		this.rttLogFileLogging = new FileLoggingBuffered(traceDir, expName, "latency", experimentId, senderId, expCondition);
	}

	public int start_connection() throws IOException {
		ControlPacket pkt = new ControlPacket(senderId, ControlPacket.SYN, this.experimentId);
		byte[] pktByte = pkt.serialize();
		DatagramPacket request = new DatagramPacket(pktByte, pktByte.length, this.serverAddr, this.serverPort);
		boolean received = false;
        while(!received) {
        	try {
        		socket.send(request);
        		byte[] buffer = new byte[PacketTrainPayload.MAX_SIZE];
        		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
        		socket.receive(p);
            	received = true;
            	ControlPacket recvPkt = new ControlPacket(p.getData());
        		if (recvPkt.getContent() == ControlPacket.SYN_ACK) {
        			setupLog();
        			return this.CONN_ESTABLISHED;
        		} else {
        			return this.FAILED_CONNECTION;
        		}
            } catch (SocketTimeoutException e) {
                // resend
            	System.out.println("Resend request!");
             }
        }
        return this.FAILED_CONNECTION;
	}

	public int sendConfig(PacketTrainConfigPacket pkt) throws IOException {
		byte[] pktByte = pkt.serialize();
		DatagramPacket request = new DatagramPacket(pktByte, pktByte.length, this.serverAddr, this.serverPort);
		socket.send(request);
		this.downlinkGapMs = pkt.download_gap_ms;
		this.uplinkGapMs = pkt.upload_gap_ms;
		this.downloadNumPackets = pkt.download_num_packets;
		this.uploadNumPackets = pkt.upload_num_packets;
		packetLogFileLogging.write(String.format("Config: sender_id=%d, server_ip=%s, packet_size=%d, uplink_gap_ms=%d, downlink_gap_ms=%d, upload_num_packets=%d, download_num_packets=%d\n",
				this.senderId, this.serverIP, this.packetSize, this.uplinkGapMs, this.downlinkGapMs, this.uploadNumPackets, this.downloadNumPackets));
		throughputLogFileLogging.write(String.format("Config: sender_id=%d, server_ip=%s, packet_size=%d, uplink_gap_ms=%d, downlink_gap_ms=%d, upload_num_packets=%d, download_num_packets=%d\n",
				this.senderId, this.serverIP, this.packetSize, this.uplinkGapMs, this.downlinkGapMs, this.uploadNumPackets, this.downloadNumPackets));
		rttLogFileLogging.write(String.format("Config: sender_id=%d, server_ip=%s, packet_size=%d, uplink_gap_ms=%d, downlink_gap_ms=%d, upload_num_packets=%d, download_num_packets=%d\n",
				this.senderId, this.serverIP, this.packetSize, this.uplinkGapMs, this.downlinkGapMs, this.uploadNumPackets, this.downloadNumPackets));
		packetLogFileLogging.flush();
		throughputLogFileLogging.flush();
		rttLogFileLogging.flush();
		byte[] buffer = new byte[PacketTrainPayload.MAX_SIZE];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		socket.receive(p);
		ControlPacket recvPkt = new ControlPacket(p.getData());
		if (recvPkt.getContent() == ControlPacket.CONFIG_ACK) {
			return this.START_SENDING;
		} else {
			return this.FAILED_CONNECTION;
		}
	}

//	public void sendData() throws IOException {
//		PacketTrainPayload pkt = new PacketTrainPayload();
//		pkt.seq_num = seqNum;
//		pkt.sent_timestamp = System.nanoTime();
//		pkt.sender_id = senderId;
//		byte[] pktByte = pkt.serialize(packetSize, randomBytes);
//		DatagramPacket request = new DatagramPacket(pktByte, pktByte.length, this.serverAddr, this.serverPort);
//		socket.send(request);
//		seqNum = seqNum + 1;
//	}
	
	public void sendDataTrain(int num_packets) throws IOException {
		long seq_num = 0;
		long timestamp = System.nanoTime();
		for (int i=0; i<num_packets; i++) {
			PacketTrainPayload pkt = new PacketTrainPayload();
			pkt.seq_num = seq_num;
			pkt.train_num = trainNum;
			pkt.sent_timestamp = timestamp;
			pkt.sender_id = senderId;
			if (i == 0) {
				pkt.pkt_info = PacketTrainPayload.PKT_INFO_START;
			} else if (i == num_packets - 1) {
				pkt.pkt_info = PacketTrainPayload.PKT_INFO_END;
			} else {
				pkt.pkt_info = 0;
			}
			byte[] pktByte = pkt.serialize(packetSize, randomBytes);
			DatagramPacket request = new DatagramPacket(pktByte, pktByte.length, this.serverAddr, this.serverPort);
			socket.send(request);
			seq_num = seq_num + 1;
		}
		trainNum = trainNum + 1;
	}

	private double calculateAverage(ArrayList<Double> throughputs2) {
		double result = 0;
		for (Double input : throughputs2) {
			result += input;
		}
		result = result / (double) throughputs2.size();
		return result;
	}

	public int recvInfoPacket(DatagramPacket p, byte[] buffer) throws IOException {
		ThroughputInfoPacket pkt = new ThroughputInfoPacket(p.getData());
		if (pkt.sender_id != this.senderId) {
			return this.FOREIGN_PACKET;
		} else {
			up_tps.add(pkt.throughput);
			System.out.println(String.format("Up --- Time %d: avg=%.2f Mbps", pkt.log_time, pkt.throughput));
			throughputLogFileLogging.write(String.format("Up --- Time %d: avg=%.2f Mbps\n", pkt.log_time, pkt.throughput));
		}
		return 0;
	}

	/**
	 * Client receive packet
	 * @return code
	 * @throws IOException
	 */
	public int recvPacket() throws IOException {
		byte[] buffer = new byte[PacketTrainPayload.MAX_SIZE];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		socket.receive(p);
		long timestamp = System.nanoTime();
		int ret = 0;
		if (p.getLength() == ThroughputInfoPacket.LENGTH) {
			ret = recvInfoPacket(p, buffer);
		} else if (p.getLength() == this.packetSize - PacketTrainPayload.UDP_HEADER_SIZE) {
			ret = recvPayload(p, buffer, timestamp);
		} else {
			System.out.println("Other packet : len=" + p.getLength());
		}
		return ret;
	}

	/**
	 * Client receive packet, log it, and calculate the bandwidth
	 * @return code
	 * @throws IOException
	 */
	public int recvPayload(DatagramPacket p, byte[] buffer, long timestamp) throws IOException {
		PacketTrainPayload payload = new PacketTrainPayload(buffer, p.getLength());
		payload.recv_timestamp = timestamp;
		if (payload.sender_id != this.senderId) {
			return this.FOREIGN_PACKET;
		} else {
			// The first packet of the train to arrive
			if (!train_tables.containsKey(payload.train_num)) {
				TrainTuple info = new TrainTuple();
				info.packet_count = 1;
				info.start_time = timestamp;
				train_tables.put(payload.train_num, info);
				if (payload.pkt_info == PacketTrainPayload.PKT_INFO_START) {
					rtts.add((timestamp - payload.sent_timestamp) / 1e6);
				}
			} 
			
			train_tables.get(payload.train_num).packet_count += 1;
			
			if (payload.pkt_info == PacketTrainPayload.PKT_INFO_END) {
				TrainTuple train_info = train_tables.get(payload.train_num); 
				// Calculate the estimated bandwidth
				long sending_duration = timestamp - train_info.start_time;
				// System.out.println("Recv packets = " + this.currPackets);
				// Do not need to do packet_count - 1 as we do not add +1 when this last packet arrive
				long amount_of_traffic = (train_info.packet_count - 1) * payload.packet_size;
				double bandwidth_mbps = ((amount_of_traffic * 8) / (sending_duration / 1e9)) / 1e6;
				// System.out.println(String.format("Dur=%.2f ms, bytes=%d", sending_duration / 1e6, amount_of_traffic));
				this.throughputs.add(bandwidth_mbps);
				// Reset parameters
				train_tables.remove(payload.train_num);
			}

			if (currLogTimestamp <= 0) {
				currLogTimestamp = timestamp;
			}
			if ((timestamp - currLogTimestamp) / 1e6 >= LOG_MS) {
				double avgThroughput = this.calculateAverage(throughputs);
				System.out.println(String.format("Down - Time %d: #thr=%d, avg=%.2f Mbps", this.logTime, throughputs.size(), avgThroughput));
				double avgRTT = this.calculateAverage(rtts);
				System.out.println(String.format("Time %d: #rtts=%d, avg=%.2f ms", this.logTime, rtts.size(), avgRTT));
				throughputLogFileLogging.write(String.format("Down - Time %d: #thr=%d, avg=%.2f Mbps\n", this.logTime, throughputs.size(), avgThroughput));
				throughputLogFileLogging.flush();
				rttLogFileLogging.write(String.format("Time %d: #nums=%d, rtt=%.2f\n", this.logTime, rtts.size(), avgRTT));
				rttLogFileLogging.flush();
				this.tps.add(avgThroughput);
				this.overall_rtts.add(avgRTT);
				this.throughputs = new ArrayList<Double>();
				this.rtts = new ArrayList<Double>();
				logTime += 1;
				currLogTimestamp = timestamp;
				packetLogFileLogging.flush();
			}
			// TODO: Log the packet arrival to a file.
			String packet_line = new StringBuilder("").append(payload.train_num).append("\t").append(payload.seq_num).append("\t").append(payload.sent_timestamp).append("\t").append(payload.recv_timestamp).append("\t").append(payload.packet_size).append("\n").toString();
			packetLogFileLogging.write(packet_line);
		}
		return 0;
	}


	public String array_to_string(ArrayList<Double> arr) {
		String result = "";
		for (int i=0; i<arr.size() - 1; i++) {
			result = result + String.format("%.2f, ", arr.get(i));
		}
		result = result + String.format("%.2f", arr.get(arr.size() - 1));
		return result;
	}
	
	public void terminateConnection() throws IOException, InterruptedException {
		ControlPacket pkt = new ControlPacket(senderId, ControlPacket.FIN);
		byte[] pktByte = pkt.serialize();
		DatagramPacket request = new DatagramPacket(pktByte, pktByte.length, this.serverAddr, this.serverPort);
		socket.send(request);
		System.out.println("Connection is terminated!");
//		Thread.sleep(3000);
		double avg_tps = this.calculateAverage(tps);
		double avg_up_tps = this.calculateAverage(up_tps);
		double avg_rtts = this.calculateAverage(overall_rtts);
		System.out.println(String.format("Avg throughputs = %.2f \t %s", avg_tps, array_to_string(tps)));
		throughputLogFileLogging.write(String.format("Average: Up=%.2f Mbps Down=%.2f Mbps", avg_up_tps, avg_tps));
		rttLogFileLogging.write(String.format("Average: rtt=%.2f", avg_rtts));
		packetLogFileLogging.close();
		throughputLogFileLogging.close();
		rttLogFileLogging.close();
	}
	
	public void closeSocket() {
		this.socket.close();
	}

}
