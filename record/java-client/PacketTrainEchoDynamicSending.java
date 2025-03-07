import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import packets.*;

public class PacketTrainEchoDynamicSending {

	public class TrainTuple {
		public long start_time;
		public int packet_count;
	}
	
	private DatagramSocket socket;
	private DatagramSocket socket2;
	private InetAddress serverAddr;

	private String serverIP;
	private int serverPort;
	private int packetSize;
	private long senderId;

	private ArrayList<Integer> gapMs;
	private long upload;
	private ArrayList<Integer> numPackets;
	private long onlyRespondToFirstAndTail;
	
	private long trainNum;
	private long currLogTimestamp;
	private long logTime;

	private ArrayList<Double> rtts;
	private ArrayList<Double> up_tps;
	private ArrayList<Double> overall_rtts;


	final public static int CONN_ESTABLISHED = 400;
	final public static int START_SENDING = 500;
	final public static int FOREIGN_PACKET = 600;
	final public static int FAILED_CONNECTION = 700;
	final private int LOG_MS = 1000;
	
	final private int MAX_CONTENT_SIZE = 1400 - Payload.UDP_HEADER_SIZE;
	
	final private int CLIENT_PORT = 7020; 
	final private int CLIENT_PORT_2 = 7021; 

	private FileLoggingBuffered packetLogFileLogging;
	private FileLoggingBuffered rttLogFileLogging;
	
	private RandomBytes randomBytes;
	
	private long experimentId = 0;
	private String traceDir;
	private String expName;
	private String expCondition = "";

	public PacketTrainEchoDynamicSending(long senderId, String serverIP, String clientIP, String clientIP2, int serverPort, int packetSize, String traceDir, String expName) throws IOException {
		this.senderId = senderId;
		this.serverIP = serverIP;
		this.serverPort = serverPort;
		this.packetSize = packetSize;
		this.traceDir = traceDir;
		this.serverAddr = InetAddress.getByName(serverIP);
		this.socket = new DatagramSocket(CLIENT_PORT, InetAddress.getByName(clientIP));
		this.socket2 = new DatagramSocket(CLIENT_PORT_2, InetAddress.getByName(clientIP2));
		this.socket.setSoTimeout(20000);
		this.socket2.setSoTimeout(20000);
		this.rtts = new ArrayList<Double>();
		this.up_tps = new ArrayList<Double>();
		this.overall_rtts = new ArrayList<Double>();
		this.randomBytes = new RandomBytes(1000);
		this.traceDir = traceDir;
		this.expName = expName;
		this.trainNum = 0;
	}
	
	public void setExperimentId(long id) {
		this.experimentId = id;
	}
	
	public void setExperimentCondition(String cond) {
    	this.expCondition = cond;
    }
	
	private void setupLog() throws IOException {
		this.rttLogFileLogging = new FileLoggingBuffered(traceDir, expName, "latency", experimentId, senderId, expCondition);
		this.packetLogFileLogging = new FileLoggingBuffered(traceDir, expName, "dynamictrain", experimentId, senderId, expCondition);
	}

	public int start_connection() throws IOException {
		ControlPacket pkt = new ControlPacket(senderId, ControlPacket.SYN, this.experimentId);
		byte[] pktByte = pkt.serialize();
		DatagramPacket request = new DatagramPacket(pktByte, pktByte.length, this.serverAddr, this.serverPort);
		boolean received = false;
        while(!received) {
        	try {
        		System.out.println(this.serverAddr);
        		System.out.println(this.serverPort);
        		System.out.println("Establishing connection for Socket1");
        		socket.send(request);
        		byte[] buffer = new byte[DynamicPacketTrainPayload.MAX_SIZE];
        		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
        		socket.receive(p);
            	ControlPacket recvPkt = new ControlPacket(p.getData());
        		if (recvPkt.getContent() == ControlPacket.SYN_ACK) {
        			System.out.println("Establishing connection for Socket2");
        			socket2.send(request);
            		buffer = new byte[DynamicPacketTrainPayload.MAX_SIZE];
            		p = new DatagramPacket(buffer, buffer.length);
            		socket2.receive(p);
                	recvPkt = new ControlPacket(p.getData());
                	if (recvPkt.getContent() == ControlPacket.SYN_ACK) {
                		setupLog();
                		received = true;
            			return this.CONN_ESTABLISHED;
                	} else {
                		return this.FAILED_CONNECTION;
                	}
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
	
	public String listToString(ArrayList<Integer> input) {
		String result = "";
		for (int i=0; i<input.size() - 1; i++) {
			result = result + input.get(i) + ",";
		}
		if (input.size() > 0) {
			result = result + input.get(input.size() - 1);
		}
		return result;
	}

	public int sendConfig(PacketTrainEchoDynamicConfigPacket configPkt) throws IOException {
		byte[] pktByte = configPkt.serialize();
		DatagramPacket request = new DatagramPacket(pktByte, pktByte.length, this.serverAddr, this.serverPort);
		socket.send(request);
		this.gapMs = configPkt.gap_ms;
		this.upload = configPkt.uplink;
		this.numPackets = configPkt.num_packets;
		this.onlyRespondToFirstAndTail = configPkt.only_echo_to_first_and_tail;
		packetLogFileLogging.write(String.format("Config: sender_id=%d, server_ip=%s, packet_size=%d, gap_ms=(%s), upload=%d, num_packets=(%s), only_respond_to_first_and_tail=%d\n",
				this.senderId, this.serverIP, this.packetSize, listToString(this.gapMs), this.upload, listToString(this.numPackets), this.onlyRespondToFirstAndTail));
		rttLogFileLogging.write(String.format("Config: sender_id=%d, server_ip=%s, packet_size=%d, gap_ms=(%s), upload=%d, upload_num_packets=(%s), only_respond_to_first_and_tail=%d\n",
				this.senderId, this.serverIP, this.packetSize, listToString(this.gapMs), this.upload, listToString(this.numPackets), this.onlyRespondToFirstAndTail));
		packetLogFileLogging.flush();
		rttLogFileLogging.flush();
		byte[] buffer = new byte[DynamicPacketTrainPayload.MAX_SIZE];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		socket.receive(p);
		ControlPacket recvPkt = new ControlPacket(p.getData());
		if (recvPkt.getContent() == ControlPacket.CONFIG_ACK) {
			return this.START_SENDING;
		} else {
			return this.FAILED_CONNECTION;
		}
	}
	
	public String add_recv_time(String pkt) {
		return pkt;
	}
	
	public void sendDataTrain(int num_packets, int train_gap_ms) throws IOException {
		long seq_num = 0;
		long timestamp = System.nanoTime();
		for (int i=0; i<num_packets; i++) {
			DynamicPacketTrainPayload pkt = new DynamicPacketTrainPayload();
			pkt.seq_num = seq_num;
			pkt.train_num = trainNum;
			pkt.sent_timestamp = timestamp;
			pkt.train_size = num_packets;
			pkt.sender_id = senderId;
			pkt.train_gap_ms = train_gap_ms;
			if (i == 0) {
				pkt.pkt_info = DynamicPacketTrainPayload.PKT_INFO_START;
			} else if (i == num_packets - 1) {
				pkt.pkt_info = DynamicPacketTrainPayload.PKT_INFO_END;
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
		if (throughputs2.size() > 0) {
			for (Double input : throughputs2) {
				result += input;
			}
			result = result / (double) throughputs2.size();
		}
		return result;
	}

	public int recvInfoPacket(DatagramPacket p, byte[] buffer) throws IOException {
		ThroughputInfoPacket pkt = new ThroughputInfoPacket(p.getData());
		if (pkt.sender_id != this.senderId) {
			return this.FOREIGN_PACKET;
		} else {
			up_tps.add(pkt.throughput);
			if (this.upload == 1) {
				System.out.println(String.format("Up --- Time %d: avg=%.2f Mbps", pkt.log_time, pkt.throughput));
			} else {
				System.out.println(String.format("RTT - Time %d: avg=%.2f ms", pkt.log_time, pkt.throughput));
			}
			
		}
		return 0;
	}

	/**
	 * Client receive packet
	 * @return code
	 * @throws IOException
	 */
	public int recvPacket() throws IOException {
		byte[] buffer = new byte[DynamicPacketTrainPayload.MAX_SIZE];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		socket2.receive(p);
		long timestamp = System.nanoTime();
		int ret = 0;
		if (p.getLength() == ThroughputInfoPacket.LENGTH) {
			ret = recvInfoPacket(p, buffer);
		} else if (p.getLength() == this.MAX_CONTENT_SIZE) {
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
		DynamicPacketTrainPayload payload = new DynamicPacketTrainPayload(buffer, p.getLength());
		payload.recv_timestamp = timestamp;
		if (payload.sender_id != this.senderId) {
			return this.FOREIGN_PACKET;
		} else {
			if (!(payload.pkt_info == DynamicPacketTrainPayload.PKT_INFO_DUMMY)) {
				double rtt = 0;
				if (this.upload == 1) {
					rtt = (payload.recv_timestamp - payload.sent_timestamp) / 1e6;
					rtts.add(rtt);
				}
				
				String packet_line = new StringBuilder("").append(payload.train_num).append("\t").append(payload.seq_num).append("\t").append(payload.sent_timestamp).append("\t").append(payload.recv_timestamp).append("\t").append(payload.packet_size).append("\t").append(rtt).append("\t").append(payload.train_size).append("\t").append(payload.train_gap_ms).append("\n").toString();
				packetLogFileLogging.write(packet_line);

				if (currLogTimestamp <= 0) {
					currLogTimestamp = timestamp;
				}
				if ((timestamp - currLogTimestamp) / 1e6 >= LOG_MS) {
					if (this.upload == 1) {
						double avgRTT = this.calculateAverage(rtts);
						System.out.println(String.format("Time %d: #rtts=%d, avg=%.2f ms", this.logTime, rtts.size(), avgRTT));
						rttLogFileLogging.write(String.format("Time %d: #nums=%d, rtt=%.2f\n", this.logTime, rtts.size(), avgRTT));
//						rttLogFileLogging.flush();
						this.overall_rtts.add(avgRTT);
						this.rtts = new ArrayList<Double>();
					}
					logTime += 1;
					currLogTimestamp = timestamp;
//					packetLogFileLogging.flush();
				}
			}
		}
		return 0;
	}
	
	public int recvAndEcho() throws IOException {
		byte[] buffer = new byte[DynamicPacketTrainPayload.MAX_SIZE];
		DatagramPacket p = new DatagramPacket(buffer, buffer.length);
		socket.receive(p);
		long timestamp = System.nanoTime();
		int ret = 0;
        
		if (p.getLength() == ThroughputInfoPacket.LENGTH) {
			ret = recvInfoPacket(p, buffer);
		} else if (p.getLength() == this.packetSize - DynamicPacketTrainPayload.UDP_HEADER_SIZE) {
			if (this.onlyRespondToFirstAndTail == 0) {
				// Send it back using socket2
				ret = recvPayload(p, buffer, timestamp);
				byte[] pkt = new byte[this.MAX_CONTENT_SIZE];
	        	System.arraycopy(p.getData(), 0, pkt, 0, this.MAX_CONTENT_SIZE);
	        	DatagramPacket request = new DatagramPacket(pkt, pkt.length, this.serverAddr, this.serverPort);
				socket2.send(request);
			} else {
				// Send it back using socket2
				ret = recvPayload(p, buffer, timestamp);
				DynamicPacketTrainPayload payload = new DynamicPacketTrainPayload(buffer, p.getLength());
				if (payload.seq_num == 0 || payload.pkt_info == DynamicPacketTrainPayload.PKT_INFO_END) {
					byte[] pkt = new byte[this.MAX_CONTENT_SIZE];
		        	System.arraycopy(p.getData(), 0, pkt, 0, this.MAX_CONTENT_SIZE);
		        	DatagramPacket request = new DatagramPacket(pkt, pkt.length, this.serverAddr, this.serverPort);
					socket2.send(request);
				}
			}
		} else {
			System.out.println("Other packet : len=" + p.getLength());
		}
		return ret;
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
		packetLogFileLogging.close();
		rttLogFileLogging.close();
	}
	
	public void closeSocket() {
		this.socket.close();
		this.socket2.close();
	}

}
