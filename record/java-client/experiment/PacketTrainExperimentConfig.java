package experiment;

public class PacketTrainExperimentConfig extends ExperimentConfig {
	
	public long experiment_id;
	public String experiment_type = ExperimentConfig.EXPERIMENT_PACKET_TRAIN;
	public long gap_ms;
	public long upload_num_packets;
	public long download_num_packets;
	public long packet_size;
	public long experiment_duration_seconds;
	
	public PacketTrainExperimentConfig(long experimentId, long gapMs, long uploadNumPackets, long downloadNumPackets, long packetSize, long experimentDuration) {
		this.experiment_id = experimentId;
		this.gap_ms = gapMs;
		this.upload_num_packets = uploadNumPackets;
		this.download_num_packets = downloadNumPackets;
		this.packet_size = packetSize;
		this.experiment_duration_seconds = experimentDuration;
	}
	
	public PacketTrainExperimentConfig(String text) {
		String[] splits = text.split("_");
		splits = splits[1].split("-");
		this.experiment_id = Long.parseLong(splits[0]);
		this.gap_ms = Long.parseLong(splits[1]);
		this.upload_num_packets = Long.parseLong(splits[2]);
		this.download_num_packets = Long.parseLong(splits[3]);
		this.packet_size = Long.parseLong(splits[4]);
		this.experiment_duration_seconds = Long.parseLong(splits[5]);
	}
	
	@Override
	public String serialize() {
		String result = String.format("%s_%d-%d-%d-%d-%d-%d", experiment_type, experiment_id, gap_ms, upload_num_packets, download_num_packets, packet_size, experiment_duration_seconds);
		return result;
	}
	
	public static boolean isPacketTrainExperiment(String config) {
		return config.contains(ExperimentConfig.EXPERIMENT_PACKET_TRAIN);
	}

}
