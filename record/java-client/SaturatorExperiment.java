import java.io.IOException;
import java.util.Random;

import experiment.SaturatorExperimentConfig;
import packets.SaturateConfigPacket;

public class SaturatorExperiment {
	private String serverIP;
	private int serverPort;
	private String clientIP;
	private long senderId;
	private SaturatorExperimentConfig config;
	private SaturateSending rateSending;
	
	private Thread sendThread;
	private Thread recvThread;
	
	private String traceDir;
	private String expName;
	private String expCond;
	
	class SendDataRunnable implements Runnable {

        private SaturateSending saturateSending;
        private int uploadRate;
        private int MIN_SLEEP_TIME_MS = 5;

        public SendDataRunnable(SaturateSending saturateSending, int uploadRate) {
            this.saturateSending = saturateSending;
            this.uploadRate = uploadRate;
        }

        public void run()
        {
            try {
                long threadId = Thread.currentThread().getId();
                int pps = (uploadRate * 1000000 / 8) / saturateSending.getPacketSize();
                int amount_of_batches_per_second = 1000 / MIN_SLEEP_TIME_MS;
                int packet_per_batch_lower = pps / amount_of_batches_per_second;
                int packet_per_batch_upper = packet_per_batch_lower + 1;
                int send_upper = pps - (amount_of_batches_per_second * packet_per_batch_lower);
                int send_lower = amount_of_batches_per_second - send_upper;
                long time1, time2, duration, sleepTime = 0;
                int count_packet = 0;
                while (true) {
                    for (int i=0; i<send_lower; i++) {
                        time1 = System.nanoTime();
                        for (int j=0; j<packet_per_batch_lower; j++) {
                    	    saturateSending.sendData();
                    	    count_packet = count_packet + 1;
                        }
                        time2 = System.nanoTime();
                        duration = (time2 - time1) / 1000000;
                        sleepTime = MIN_SLEEP_TIME_MS - duration;
                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime);
                        }
                    } 
                    for (int i=0; i<send_upper; i++) {
                        time1 = System.nanoTime();
                        for (int j=0; j<packet_per_batch_upper; j++) {
                            saturateSending.sendData();
                            count_packet = count_packet + 1;
                        }
                        time2 = System.nanoTime();
                        duration = (time2 - time1) / 1000000;
                        sleepTime = MIN_SLEEP_TIME_MS - duration;
                        if (sleepTime > 0) {
                       	    Thread.sleep(sleepTime);
                        }
                    }
                    count_packet = 0;
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

        private SaturateSending saturateSending;

        public RecvDataRunnable(SaturateSending saturateSending) {
            this.saturateSending = saturateSending;
        }

        public void run()
        {
            try {
                long threadId = Thread.currentThread().getId();
                int count = 0;
                while (true) {
                	saturateSending.recvPacket();
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
	
	public SaturatorExperiment(String serverIP, int serverPort, String clientIP, SaturatorExperimentConfig config, String traceDir, String expName, String expCond) {
		this.serverIP = serverIP;
		this.serverPort = serverPort;
		this.config = config;
		this.traceDir = traceDir;
		this.expName = expName;
		this.expCond = expCond;
		this.clientIP = clientIP;
	}
	
	private long genSenderId() {
		Random rand = new Random();
		return rand.nextInt(Integer.MAX_VALUE);
	}
	
	public int start() throws IOException, InterruptedException {
		this.senderId = genSenderId();
		this.rateSending = new SaturateSending(senderId, serverIP, clientIP, serverPort, traceDir, expName);
		rateSending.setExperimentId(config.experiment_id);
		rateSending.setExperimentCondition(expCond);
		int code = rateSending.start_connection();
		if (code == SaturateSending.CONN_ESTABLISHED) {
			System.out.println("Successfull!!");
		} else {
			System.out.println("Fail to start sending!");
		}
		
		SaturateConfigPacket configPkt = new SaturateConfigPacket(senderId, config.upload_rate, config.download_rate, config.packet_size);
		code = rateSending.sendConfig(configPkt);
		if (code == SaturateSending.START_SENDING) {
			System.out.println("Got through!!");
		} else {
			System.out.println("Wrong response from server");
		}
		sendThread = new Thread(new SendDataRunnable(rateSending, (int) config.upload_rate));
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
