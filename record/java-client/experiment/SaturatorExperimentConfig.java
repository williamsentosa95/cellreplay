package experiment;

public class SaturatorExperimentConfig extends ExperimentConfig {
	public long experiment_id;
	public final String experiment_type = ExperimentConfig.EXPERIMENT_SATURATOR; 
	public long upload_rate;
	public long download_rate;
	public long packet_size;
	public long experiment_duration_seconds;
	
	public SaturatorExperimentConfig(long experimentId, long uploadRate, long downloadRate, long packetSize, long experimentDuration) {
		this.experiment_id = experimentId;
		this.upload_rate = uploadRate;
		this.download_rate = downloadRate;
		this.packet_size = packetSize;
		this.experiment_duration_seconds = experimentDuration;
	}
	
	public SaturatorExperimentConfig(String text) {
		String[] splits = text.split("_");
		splits = splits[1].split("-");
		this.experiment_id = Long.parseLong(splits[0]);
		this.upload_rate = Long.parseLong(splits[1]);
		this.download_rate = Long.parseLong(splits[2]);
		this.packet_size = Long.parseLong(splits[3]);
		this.experiment_duration_seconds = Long.parseLong(splits[4]);
	}

	@Override
	public String serialize() {
		String result = String.format("%s_%d-%d-%d-%d-%d", experiment_type, experiment_id, upload_rate, download_rate, packet_size, experiment_duration_seconds);
		return result;
	}
	
	public static boolean isSaturatorExperiment(String config) {
		return config.contains(ExperimentConfig.EXPERIMENT_SATURATOR);
	}
	
}
