package packets;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class PacketTrainEchoDynamicConfigPacket {
	final static public int LENGTH = 1000;
	final static public int UDP_HEADER_SIZE = 42;
	final static public int PACKET_TRAIN_ECHO_DYNAMIC_CONFIG_PACKET_SIGNATURE = 1234;
	final static public int LENGTH_OF_LONG_VARIABLES = 5;
	
	public long sender_id;
	public long packet_size;
	public long uplink;
	public long only_echo_to_first_and_tail;
	public ArrayList<Integer> gap_ms;
	public ArrayList<Integer> num_packets;
	
	public PacketTrainEchoDynamicConfigPacket(long senderId, ArrayList<Integer> gapMs, ArrayList<Integer> numPackets, long packetSize, long uplink, long onlyEchoToFirstAndTail) {
		this.sender_id = senderId;
		this.gap_ms = gapMs;
		this.uplink = uplink;
		this.num_packets = numPackets;
		this.packet_size = packetSize;
		this.only_echo_to_first_and_tail = onlyEchoToFirstAndTail;
	}
	
	public PacketTrainEchoDynamicConfigPacket(byte[] stream) {
		ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOfRange(stream, 0, LENGTH_OF_LONG_VARIABLES * Long.BYTES));
		LongBuffer longBuffer = bb.asLongBuffer();
		long signature = longBuffer.get(0);
		if (signature == this.PACKET_TRAIN_ECHO_DYNAMIC_CONFIG_PACKET_SIGNATURE) {
			this.sender_id = longBuffer.get(1);
			this.packet_size = longBuffer.get(2);
			this.uplink = longBuffer.get(3);
			this.only_echo_to_first_and_tail = longBuffer.get(4);
			String config = new String(Arrays.copyOfRange(stream, LENGTH_OF_LONG_VARIABLES * Long.BYTES, LENGTH));
			System.out.println("Config = " + config);
		} else {
			System.out.println("Get a wrong signature");
		}
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
	
	private byte[] longToBytes(long input) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
	    buffer.putLong(input);
	    return buffer.array();
	}
	
	public byte[] serialize() {
		byte[] result = new byte[LENGTH];
		int currentIdx = 0;
		byte[] temp = longToBytes(this.PACKET_TRAIN_ECHO_DYNAMIC_CONFIG_PACKET_SIGNATURE);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = longToBytes(sender_id);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = longToBytes(packet_size);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = longToBytes(uplink);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = longToBytes(only_echo_to_first_and_tail);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		// Serialize both gap_ms and num packets to string
		String info = listToString(gap_ms) + "-" + listToString(num_packets);
		temp = info.getBytes();
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		
	    return result;
	}
	
	public String toString() {
		String result = "";
		result = String.format("Sender: %d, %d %s %s %d %d", this.sender_id,  this.uplink, listToString(this.gap_ms), listToString(this.num_packets), this.packet_size, this.only_echo_to_first_and_tail);
		return result;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
	}

}

