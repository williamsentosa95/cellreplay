import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import experiment.PacketTrainEchoDynamicExperimentConfig;
import packets.PacketTrainEchoDynamicConfigPacket;

public class PacketTrainEchoDynamicExperiment {
	
	private String serverIP;
	private int serverPort;
	private String clientIP;
	private String clientIP2;
	
	private PacketTrainEchoDynamicExperimentConfig config;
	
	private Thread sendThread;
	private Thread recvThread;
	
	private String traceDir;
	private String expName;
	private String expCond;
	
	private long uplink;
	
	private static final int DEFAULT_PACKET_GAP_MS = 5;
	private static final int GAP_BETWEEN_EXPERIMENT_MS = 500;
	private static final int TRAIN_SENDING_DURATION = 2000;
	
	private long genSenderId() {
		Random rand = new Random();
		return rand.nextInt(Integer.MAX_VALUE);
	}
	
	class SendDataRunnable implements Runnable {

        private PacketTrainEchoDynamicSending packetTrainSending;
        private ArrayList<Integer> sendingGapMs;
        private ArrayList<Integer> numPackets;

        public SendDataRunnable(PacketTrainEchoDynamicSending packetTrainSending, ArrayList<Integer> sendingGapMs, ArrayList<Integer> numPackets) {
            this.packetTrainSending = packetTrainSending;
            this.sendingGapMs = sendingGapMs;
            this.numPackets = numPackets;
        }

        public void run()
        {
            try {
                long threadId = Thread.currentThread().getId();
                
                while (true) {
                	for(int i=0; i<sendingGapMs.size(); i++) {
                		for (int j=0; j<numPackets.size(); j++) {
                			int num_trials = TRAIN_SENDING_DURATION / sendingGapMs.get(i);
                			for (int k=0; k<num_trials; k++) {
                				packetTrainSending.sendDataTrain(numPackets.get(j), sendingGapMs.get(i));
                    			Thread.sleep(sendingGapMs.get(i));
                			}
                			Thread.sleep(GAP_BETWEEN_EXPERIMENT_MS);
                		}
                	}
                    
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

        private PacketTrainEchoDynamicSending packetTrainSending;

        public RecvDataRunnable(PacketTrainEchoDynamicSending packetTrainSending) {
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
    
    class RecvAndSendDataRunnable implements Runnable {

        private PacketTrainEchoDynamicSending packetTrainSending;

        public RecvAndSendDataRunnable(PacketTrainEchoDynamicSending packetTrainSending) {
            this.packetTrainSending = packetTrainSending;
        }

        public void run()
        {
            try {
                long threadId = Thread.currentThread().getId();
                while (true) {
                	packetTrainSending.recvAndEcho();
                }
            }
            catch (Exception e) {
                // Throwing an exception
                System.out.println("Exception is caught");
                e.printStackTrace();
            }
        }
    }
	
	public PacketTrainEchoDynamicExperiment(String serverIP, int serverPort, String clientIP, String clientIP2, long uplink, PacketTrainEchoDynamicExperimentConfig config, String traceDir, String expName, String expCond ) {
		this.serverIP = serverIP;
		this.serverPort = serverPort;
		this.config = config;
		this.traceDir = traceDir;
		this.expName = expName;
		this.expCond = expCond;
		this.clientIP = clientIP;
		this.clientIP2 = clientIP2;
		this.uplink = uplink;
	}
	
	public int start() throws IOException, InterruptedException {
		long senderId = genSenderId();
		PacketTrainEchoDynamicSending rateSending = new PacketTrainEchoDynamicSending(senderId, serverIP, clientIP, clientIP2, serverPort, (int) config.packet_size, traceDir, expName);
		rateSending.setExperimentId(config.experiment_id);
		rateSending.setExperimentCondition(expCond);
		System.out.println("Packet Train exp id = " + config.experiment_id);
		int code = rateSending.start_connection();
		if (code == PacketTrainSending.CONN_ESTABLISHED) {
			System.out.println("Successfull!!");
		} else {
			System.out.println("Fail to start sending!");
			return 1;
		}
		PacketTrainEchoDynamicConfigPacket configPkt = new PacketTrainEchoDynamicConfigPacket(senderId, config.gap_ms, config.num_packets, config.packet_size, config.uplink, config.echo_to_only_first_and_tail);
		System.out.println(configPkt.toString());
		code = rateSending.sendConfig(configPkt);
		if (code == PacketTrainSending.START_SENDING) {
			System.out.println("Got through!!");
			if (uplink == 1) {
				sendThread = new Thread(new SendDataRunnable(rateSending, config.gap_ms, config.num_packets));
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
						break;
					} else {
						Thread.sleep(1000);
					}
				}
			} else {
				System.out.println("RECV and SEND!!");
				recvThread = new Thread(new RecvAndSendDataRunnable(rateSending));
				recvThread.start();
				
				long start_timestamp = System.nanoTime();
				while(true) {
					long timestamp = System.nanoTime();
					if ((timestamp - start_timestamp) / 1e9 > config.experiment_duration_seconds) {
						rateSending.terminateConnection();
						recvThread.stop();
						rateSending.closeSocket();
						break;
					} else {
						Thread.sleep(1000);
					}
				}
			}
		} else {
			System.out.println("Wrong response from server");
		}
		
		return 0;
	}
}

