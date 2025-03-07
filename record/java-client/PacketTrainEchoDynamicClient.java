import java.io.IOException;
import java.util.ArrayList;
import java.nio.file.*;

import experiment.PacketTrainEchoDynamicExperimentConfig;

public class PacketTrainEchoDynamicClient {
	
	public static ArrayList<Integer> stringToList(String input) {
		ArrayList<Integer> result = new ArrayList<>();
		String[] tokens = input.split(",");
		for (int i = 0; i<tokens.length; i++) {
			result.add(Integer.parseInt(tokens[i]));
		}
		return result;
	}
	
	public static void main(String[] args) {
		String ipAddr = "127.0.0.1";
		String clientIP = "127.0.0.1";
		String clientIP2 = "127.0.0.1";
		int serverPort = 9011;
		String traceDir = "";
		
		// Experiment configs
		int running_duration_seconds = 20;
		int packetSize = 1400;
		int uplink = 0;
		int echoToOnlyFirstAndTail = 1;
		long expId = 100;
		String expName = "DynamicPacketTrainEcho";
		String expCond = "test";
		
		ArrayList<Integer> numPackets = new ArrayList<>();
		numPackets.add(10);
		numPackets.add(50);
		
		ArrayList<Integer> gapMs = new ArrayList<>();
		gapMs.add(50);
		gapMs.add(20);
		
		if (args.length < 12) {
			System.err.println("Usage: <program> <trace_out_dir> <server_ip> <server_port> <client_ip> <uplink> <running_time_sec> <packet_size> <list_of_gap_ms> <list_of_num_packets> <expId> <expName> <expCond>");
			System.out.println("Args length = " + args.length);
			System.exit(0);
		} else {
			traceDir = args[0];
			ipAddr = args[1];
			serverPort = Integer.parseInt(args[2]);
			clientIP = args[3];
			clientIP2 = clientIP;
			uplink = Integer.parseInt(args[4]);
			running_duration_seconds = Integer.parseInt(args[5]);
			packetSize = Integer.parseInt(args[6]);
			gapMs = stringToList(args[7]);
			numPackets = stringToList(args[8]);
			echoToOnlyFirstAndTail = 1;
			expId = Long.parseLong(args[9]);
			expName = args[10];
			expCond = args[11];
		}

		Path path = Paths.get(traceDir);

        if (!(Files.exists(path) && Files.isDirectory(path))) {
            System.out.printf("PacketTrain: trace_out_dir %s does not exists!\n", traceDir);
			System.exit(0);
        }
		
		PacketTrainEchoDynamicExperimentConfig packetTrainConfig = new PacketTrainEchoDynamicExperimentConfig(expId, gapMs, uplink, numPackets, packetSize, echoToOnlyFirstAndTail, running_duration_seconds);
		PacketTrainEchoDynamicExperiment exp = new PacketTrainEchoDynamicExperiment(ipAddr, serverPort, clientIP, clientIP2, uplink, packetTrainConfig, traceDir, expName, expCond);
		try {
			int code = exp.start();
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
