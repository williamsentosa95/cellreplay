import java.io.IOException;
import java.nio.file.*;

import experiment.SaturatorExperimentConfig;


public class SaturatorClient {
	
	public static void main(String[] args) {
		String ipAddr = "127.0.0.1";
		String clientIP = "127.0.0.1";
		int serverPort = 9002;
		int packetSize = 1400;
		// Send experiment configs
		int uploadRate = 1;
		int downloadRate = 1;
		int running_duration_seconds = 10;
		String traceDir = "";
		
		long experimentId = 0;
		String expName = "Saturator";
		String expCondition = "record";
		
		if (args.length < 11) {
			System.err.println("Usage: <program> <trace_out_dir> <server_ip> <server_port> <client_ip> <running_time_sec> <packet_size> <upload_rate> <download_rate> <out_dir> <exp_id> <exp_name> <exp_condition>");
			System.exit(0);
		} else {
			traceDir = args[0];
			ipAddr = args[1];
			serverPort = Integer.parseInt(args[2]);
			clientIP = args[3];
			running_duration_seconds = Integer.parseInt(args[4]);
			packetSize = Integer.parseInt(args[5]);
			uploadRate = Integer.parseInt(args[6]);
			downloadRate = Integer.parseInt(args[7]);
			experimentId = Long.parseLong(args[8]);
			expName = args[9];
			expCondition = args[10];
		}

		Path path = Paths.get(traceDir);

        if (!(Files.exists(path) && Files.isDirectory(path))) {
            System.out.printf("Saturator: trace_out_dir %s does not exists!\n", traceDir);
			System.exit(0);
        }
		
		SaturatorExperimentConfig saturatorConfig = new SaturatorExperimentConfig(experimentId, uploadRate, downloadRate, packetSize, running_duration_seconds);
		SaturatorExperiment exp = new SaturatorExperiment(ipAddr, serverPort, clientIP, saturatorConfig, traceDir, expName, expCondition);
		try {
			int code = exp.start();
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}

}
