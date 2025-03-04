package experiment;

import java.nio.ByteBuffer;

public abstract class ExperimentConfig {
	public static final String EXPERIMENT_LATENCY = "Latency";
	public static final String EXPERIMENT_SATURATOR = "Saturator";
	public static final String EXPERIMENT_PACKET_TRAIN = "PacketTrain";
	public static final String EXPERIMENT_PACKET_TRAIN_ECHO = "PacketTrainEcho";
	public static final String EXPERIMENT_READ_TRACES = "ReadTraces";
	public static final String EXPERIMENT_MAIN = "Experiments";
	
	public abstract String serialize();
}
