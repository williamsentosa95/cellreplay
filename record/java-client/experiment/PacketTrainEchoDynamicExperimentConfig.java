package experiment;

import java.util.ArrayList;

public class PacketTrainEchoDynamicExperimentConfig extends ExperimentConfig {
	
	public long experiment_id;
	public String experiment_type = ExperimentConfig.EXPERIMENT_PACKET_TRAIN;
	public ArrayList<Integer> gap_ms;
	public long uplink;
	public ArrayList<Integer> num_packets;
	public long packet_size;
	public long echo_to_only_first_and_tail;
	public long experiment_duration_seconds;
	
	public PacketTrainEchoDynamicExperimentConfig(long experimentId, ArrayList<Integer> gapMs, long uplink, ArrayList<Integer> numPackets, long packetSize, long echoToOnlyFirstAndTail, long experimentDuration) {
		this.experiment_id = experimentId;
		this.gap_ms = gapMs;
		this.num_packets = numPackets;
		this.uplink = uplink;
		this.packet_size = packetSize;
		this.echo_to_only_first_and_tail = echoToOnlyFirstAndTail;
		this.experiment_duration_seconds = experimentDuration;
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
	
	public ArrayList<Integer> stringToList(String input) {
		ArrayList<Integer> result = new ArrayList<>();
		String[] tokens = input.split(",");
		for (int i = 0; i<tokens.length; i++) {
			result.add(Integer.parseInt(tokens[i]));
		}
		return result;
	}
	
	public PacketTrainEchoDynamicExperimentConfig(String text) {
		String[] splits = text.split("_");
		splits = splits[1].split("-");
		this.experiment_id = Long.parseLong(splits[0]);
		this.gap_ms = stringToList(splits[1]);
		this.num_packets = stringToList(splits[2]);
		this.uplink = Long.parseLong(splits[3]);
		this.packet_size = Long.parseLong(splits[4]);
		this.echo_to_only_first_and_tail = Long.parseLong(splits[5]);
		this.experiment_duration_seconds = Long.parseLong(splits[6]);
	}
	
	@Override
	public String serialize() {
		String result = String.format("%s_%d-%s-%s-%d-%d-%d-%d", experiment_type, experiment_id, listToString(gap_ms), listToString(num_packets), uplink, packet_size, echo_to_only_first_and_tail, experiment_duration_seconds);
		return result;
	}
	
	public static boolean isPacketTrainEchoExperiment(String config) {
		return config.contains(ExperimentConfig.EXPERIMENT_PACKET_TRAIN);
	}

}
