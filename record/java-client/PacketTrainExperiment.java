import java.io.IOException;
import java.util.Random;

import experiment.PacketTrainExperimentConfig;
import packets.PacketTrainConfigPacket;

public class PacketTrainExperiment {
	
	private String serverIP;
	private int serverPort;
	private String clientIP;
	
	private PacketTrainExperimentConfig config;
	
	private Thread sendThread;
	private Thread recvThread;
	
	private String traceDir;
	private String expName;
	private String expCond;
	
	private long genSenderId() {
		Random rand = new Random();
		return rand.nextInt(Integer.MAX_VALUE);
	}
	
	class SendDataRunnable implements Runnable {

        private PacketTrainSending packetTrainSending;
        private int sendingGapMs;
        private int numPackets;

        public SendDataRunnable(PacketTrainSending packetTrainSending, int sendingGapMs, int numPackets) {
            this.packetTrainSending = packetTrainSending;
            this.sendingGapMs = sendingGapMs;
            this.numPackets = numPackets;
        }

        public void run()
        {
            try {
                long threadId = Thread.currentThread().getId();
                while (true) {
                    packetTrainSending.sendDataTrain(numPackets);
                    Thread.sleep(sendingGapMs);
                }
            }
            catch (Exception e) {
                // Throwing an exception
                System.out.println("Exception is caught");
                e.printStackTrace();
            }
        }
    }

    class RecvDataRunnable implements Runnable {

        private PacketTrainSending packetTrainSending;

        public RecvDataRunnable(PacketTrainSending packetTrainSending) {
            this.packetTrainSending = packetTrainSending;
        }

        public void run()
        {
            try {
                long threadId = Thread.currentThread().getId();
                int count = 0;
                while (true) {
                	packetTrainSending.recvPacket();
                	// Print the info about the rate here!!!
                }
            }
            catch (Exception e) {
                // Throwing an exception
                System.out.println("Exception is caught");
                e.printStackTrace();
            }
        }
    }
	
	public PacketTrainExperiment(String serverIP, int serverPort, String clientIP, PacketTrainExperimentConfig config, String traceDir, String expName, String expCond ) {
		this.serverIP = serverIP;
		this.serverPort = serverPort;
		this.config = config;
		this.traceDir = traceDir;
		this.expName = expName;
		this.expCond = expCond;
		this.clientIP = clientIP;
	}
	
	public int start() throws IOException, InterruptedException {
		long senderId = genSenderId();
		PacketTrainSending rateSending = new PacketTrainSending(senderId, serverIP, clientIP, serverPort, (int) config.packet_size, traceDir, expName);
		rateSending.setExperimentId(config.experiment_id);
		rateSending.setExperimentCondition(expCond);
		System.out.println("Packet Train exp id = " + config.experiment_id);
		int code = rateSending.start_connection();
		if (code == PacketTrainSending.CONN_ESTABLISHED) {
			System.out.println("Successfull!!");
		} else {
			System.out.println("Fail to start sending!");
		}
		// Send experiment configs
		PacketTrainConfigPacket configPkt = new PacketTrainConfigPacket(senderId, config.gap_ms, config.gap_ms, config.upload_num_packets, config.download_num_packets, config.packet_size);
		code = rateSending.sendConfig(configPkt);
		if (code == PacketTrainSending.START_SENDING) {
			System.out.println("Got through!!");
		} else {
			System.out.println("Wrong response from server");
		}
		sendThread = new Thread(new SendDataRunnable(rateSending, (int) config.gap_ms, (int) config.upload_num_packets));
		recvThread = new Thread(new RecvDataRunnable(rateSending));
		sendThread.start();
		recvThread.start();
		
		long start_timestamp = System.nanoTime();
		while(true) {
			long timestamp = System.nanoTime();
			if ((timestamp - start_timestamp) / 1e9 > config.experiment_duration_seconds) {
				rateSending.terminateConnection();
				sendThread.stop();
				recvThread.stop();
				rateSending.closeSocket();
				return 0;
			} else {
				Thread.sleep(1000);
			}
		}
	}
}
