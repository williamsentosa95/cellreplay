import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import packets.ControlPacket;
import packets.FileLogging;
import packets.FileLoggingBuffered;
import packets.SaturateConfigPacket;
import packets.ThroughputInfoPacket;

public class SaturateSending {
	private DatagramSocket socket;
	private InetAddress serverAddr;
	
	private String serverIP;
	private int serverPort;
	private long packetSize;
	private long senderId;
	
	private long uploadRate;
	private long downloadRate;

	private long seqNum;
	private long currPackets;
	private long currLogTimestamp;
	private long logTime;
	
	final public static int CONN_ESTABLISHED = 400;
	final public static int START_SENDING = 500;
	final public static int FOREIGN_PACKET = 600;
	final public static int FAILED_CONNECTION = 700;
	final private int LOG_MS = 1000;
	
	final private int CLIENT_PORT = 7000;
	
	private FileLoggingBuffered packetLogFileLogging;
	private FileLoggingBuffered throughputLogFileLogging;
	
	private RandomBytes random;
	
	private ArrayList<Double> tps;
	private ArrayList<Double> up_tps;
	
	private long experimentId = 0;
	private String traceDir;
	private String expName;
	private String expCondition = "";
	
	public SaturateSending(long senderId, String serverIP, String clientIP, int serverPort, String traceDir, String expName) throws IOException {
		this.senderId = senderId;
		this.serverIP = serverIP;
		this.serverPort = serverPort;
		this.serverAddr = InetAddress.getByName(serverIP);
		this.socket = new DatagramSocket(CLIENT_PORT, InetAddress.getByName(clientIP));
		this.seqNum = 0;
		this.random = new RandomBytes(1000);
		this.tps = new ArrayList<Double>();
		this.up_tps = new ArrayList<Double>();
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
		this.packetLogFileLogging = new FileLoggingBuffered(traceDir, expName, "saturator", experimentId, senderId, expCondition);
		this.throughputLogFileLogging = new FileLoggingBuffered(traceDir, expName, "throughput", experimentId, senderId, expCondition);
	}
	
	public int start_connection() throws IOException {
		ControlPacket pkt = new ControlPacket(senderId, ControlPacket.SYN, this.experimentId);
		byte[] pktByte = pkt.serialize();
		DatagramPacket request = new DatagramPacket(pktByte, pktByte.length, this.serverAddr, this.serverPort);
		
		boolean received = false;
        while(!received) {
        	try {
        		socket.send(request);
        		byte[] buffer = new byte[Payload.MAX_SIZE];
        		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
        		socket.receive(p);
            	received = true;
            	ControlPacket recvPkt = new ControlPacket(p.getData());
        		if (recvPkt.getContent() == ControlPacket.SYN_ACK) {
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
	
	public int sendConfig(SaturateConfigPacket pkt) throws IOException {
		byte[] pktByte = pkt.serialize();
		DatagramPacket request = new DatagramPacket(pktByte, pktByte.length, this.serverAddr, this.serverPort);
		socket.send(request);
		System.out.println(pkt);
		this.uploadRate = pkt.upload_rate;
		this.downloadRate = pkt.download_rate;
		this.packetSize = pkt.packet_size;
		setupLog();
		packetLogFileLogging.write(String.format("Config: sender_id=%d, server_ip=%s, packet_size=%d, upload_rate=%d, download_rate=%d\n",
				this.senderId, this.serverIP, this.packetSize, this.uploadRate, this.downloadRate));
		packetLogFileLogging.write(String.format("SEQ_NUM\tSEND_TIMESTAMP\tRECV_TIMESTAMP\tPACKET_SIZE\n"));
		throughputLogFileLogging.write(String.format("Config: sender_id=%d, server_ip=%s, packet_size=%d, upload_rate=%d, download_rate=%d\n",
				this.senderId, this.serverIP, this.packetSize, this.uploadRate, this.downloadRate));
		packetLogFileLogging.flush();
		throughputLogFileLogging.flush();
		byte[] buffer = new byte[Payload.MAX_SIZE];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		socket.receive(p);
		ControlPacket recvPkt = new ControlPacket(p.getData());
		if (recvPkt.getContent() == ControlPacket.CONFIG_ACK) {
			return this.START_SENDING;
		} else {
			return this.FAILED_CONNECTION;
		}
	}
	
	public void sendData() throws IOException {
		Payload pkt = new Payload();
		pkt.seq_num = seqNum;
		pkt.sent_timestamp = System.nanoTime();
		pkt.sender_id = senderId;
		byte[] pktByte = pkt.serialize((int) packetSize, random);
		DatagramPacket request = new DatagramPacket(pktByte, pktByte.length, this.serverAddr, this.serverPort);
		socket.send(request);
		seqNum = seqNum + 1;
	}
	
	public int recvInfoPacket(DatagramPacket p, byte[] buffer) throws IOException {
		ThroughputInfoPacket pkt = new ThroughputInfoPacket(p.getData());
		if (pkt.sender_id != this.senderId) {
			return this.FOREIGN_PACKET;
		} else {
			up_tps.add(pkt.throughput);
			throughputLogFileLogging.write(String.format("Up --- Time %d: avg=%.2f Mbps\n", pkt.log_time, pkt.throughput));
			System.out.println(String.format("Up - Time %d: avg_throughputs=%.2f", pkt.log_time, pkt.throughput));
		}
		return 0;
	}
	
	public int getPacketSize() {
		return (int) this.packetSize;
	}
	
	/**
	 * Client receive packet
	 * @return code
	 * @throws IOException
	 */
	public int recvPacket() throws IOException {
		byte[] buffer = new byte[Payload.MAX_SIZE];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		socket.receive(p);
		long timestamp = System.nanoTime();
		int ret = 0;
		if (p.getLength() == ThroughputInfoPacket.LENGTH) {
			ret = recvInfoPacket(p, buffer);
		} else if (p.getLength() == this.packetSize - Payload.UDP_HEADER_SIZE) {
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
		Payload payload = new Payload(buffer, p.getLength());
		payload.recv_timestamp = timestamp;
		if (payload.sender_id != this.senderId) {
			return this.FOREIGN_PACKET;
		} else {
			this.currPackets += 1;
			if (currLogTimestamp <= 0) {
				currLogTimestamp = timestamp;
			}
			if ((timestamp - currLogTimestamp) / 1e6 >= LOG_MS) {
				double avgThroughput = ((this.currPackets * 8 * this.packetSize) / 1e6) / (LOG_MS / 1e3);
				System.out.println(String.format("Download - Time %d: throughput=%.2f", this.logTime, avgThroughput));
				tps.add(avgThroughput);
				throughputLogFileLogging.write(String.format("Down - Time %d: #thr=%d, avg=%.2f Mbps\n", this.logTime, 1, avgThroughput));
				throughputLogFileLogging.flush();
				logTime += 1;
				currLogTimestamp = timestamp;
				this.currPackets = 0;
				packetLogFileLogging.flush();
			}
			// TODO: Log the packet arrival to a file.
			String packet_line = new StringBuilder("").append(payload.seq_num).append("\t").append(payload.sent_timestamp).append("\t").append(payload.recv_timestamp).append("\t").append(payload.packet_size).append("\n").toString();
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
	
	private double calculateAverage(ArrayList<Double> throughputs2) {
		double result = 0;
		for (Double input : throughputs2) {
			result += input;
		}
		result = result / (double) throughputs2.size();
		return result;
	}
	
	public void terminateConnection() throws IOException, InterruptedException {
		ControlPacket pkt = new ControlPacket(senderId, ControlPacket.FIN);
		byte[] pktByte = pkt.serialize();
		DatagramPacket request = new DatagramPacket(pktByte, pktByte.length, this.serverAddr, this.serverPort);
		socket.send(request);
		System.out.println("Connection is terminated!");
		Thread.sleep(3000);
		double avg_tps = this.calculateAverage(tps);
		double avg_up_tps = this.calculateAverage(up_tps);
		System.out.println(String.format("Avg throughputs = %.2f \t %s", avg_tps, array_to_string(tps)));
		throughputLogFileLogging.write(String.format("Average: Up=%.2f Mbps Down=%.2f Mbps", avg_up_tps, avg_tps));
		packetLogFileLogging.close();
		throughputLogFileLogging.close();
	}
	
	public void closeSocket() {
		this.socket.close();
	}
	
	
}
