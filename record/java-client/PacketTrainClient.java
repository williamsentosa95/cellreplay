import java.io.IOException;
import java.nio.file.*;

import experiment.PacketTrainExperimentConfig;

public class PacketTrainClient {
	
	public static void main(String[] args) {
		String ipAddr = "127.0.0.1";
		String clientIP = "127.0.0.1";
		int serverPort = 9003;
		String traceDir = "/home/william/net-traces";
		
		// Experiment configs
		int running_duration_seconds = 10;
		int packetSize = 1400;
		int uploadGapMs = 50;
		int uploadNumPackets = 300;
		int downloadNumPackets = 500;
		
		long expId = 100;
		String expName = "PacketTrain";
		String expCond = "test";
		
		if (args.length < 12) {
			System.err.println("Usage: <program> <trace_out_dir> <server_ip> <server_port> <client_ip> <running_time_sec> <packet_size> <upload_gap_ms> <upload_num_packets> <download_num_packets> <expId> <expName> <expCond>");
			System.exit(0);
		} else {
			traceDir = args[0];
			ipAddr = args[1];
			serverPort = Integer.parseInt(args[2]);
			clientIP = args[3];
			running_duration_seconds = Integer.parseInt(args[4]);
			packetSize = Integer.parseInt(args[5]);
			uploadGapMs = Integer.parseInt(args[6]);
			uploadNumPackets = Integer.parseInt(args[7]);
			downloadNumPackets = Integer.parseInt(args[8]);
			expId = Long.parseLong(args[9]);
			expName = args[10];
			expCond = args[11];
		}

		Path path = Paths.get(traceDir); // Change to actual path

        if (!(Files.exists(path) && Files.isDirectory(path))) {
            System.out.printf("PacketTrain: trace_out_dir %s does not exists!\n", traceDir);
			System.exit(0);
        }
		
		PacketTrainExperimentConfig packetTrainConfig = new PacketTrainExperimentConfig(expId, uploadGapMs, uploadNumPackets, downloadNumPackets, packetSize, running_duration_seconds);
		PacketTrainExperiment exp = new PacketTrainExperiment(ipAddr, serverPort, clientIP, packetTrainConfig, traceDir, expName, expCond);
		try {
			int code = exp.start();
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
