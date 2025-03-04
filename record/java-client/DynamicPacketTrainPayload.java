import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.Random;

public class DynamicPacketTrainPayload {

	final static public int MAX_SIZE = 1400;
	final static public int UDP_HEADER_SIZE = 42;
	final static public int PKT_INFO_START = 1;
	final static public int PKT_INFO_END = 2;
	final static public int PKT_INFO_DUMMY = 10;
	
	public long seq_num;
	public long sent_timestamp;
	public long recv_timestamp;
	public long sender_id;
	public long train_num;
	public long pkt_info;
	public long train_size;
	public long train_gap_ms;
	public int packet_size; // Packet size
	
	public DynamicPacketTrainPayload(byte[] stream, int length) {
		this.packet_size = length + UDP_HEADER_SIZE;
		ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOfRange(stream, 0, 64));
		LongBuffer longBuffer = bb.asLongBuffer();
		this.seq_num = longBuffer.get(0);
		this.sent_timestamp = longBuffer.get(1);
		this.recv_timestamp = longBuffer.get(2);
		this.sender_id = longBuffer.get(3);
		this.train_num = longBuffer.get(4);
		this.pkt_info = longBuffer.get(5);
		this.train_size = longBuffer.get(6);
		this.train_gap_ms = longBuffer.get(7);
	}
	
	public DynamicPacketTrainPayload() {
		// TODO Auto-generated constructor stub
	}

	private byte[] longToBytes(long input) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
	    buffer.putLong(input);
	    return buffer.array();
	}
	
	
	public byte[] serialize(int length, RandomBytes randomBytes) {
		byte[] result = new byte[length - UDP_HEADER_SIZE];
		int currentIdx = 0;
		this.packet_size = length;
		// Seq_num
		byte[] temp = longToBytes(seq_num);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		// Sent_timestamp
		temp = longToBytes(sent_timestamp);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		// Recv_timestamp
		temp = longToBytes(recv_timestamp);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		// Sender_id
		temp = longToBytes(sender_id);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		// Train_num
		temp = longToBytes(train_num);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		// Pkt_info
		temp = longToBytes(pkt_info);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		// train_size
		temp = longToBytes(train_size);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		// gap_ms
		temp = longToBytes(train_gap_ms);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		// Random payload
		byte[] rndBytes = randomBytes.get_random_bytes(result.length - currentIdx);
		for (int i=0; i<rndBytes.length; i++) {
			result[currentIdx] = rndBytes[i];
			currentIdx += 1;
		}
	    return result;
	}
	
	public byte[] serialize(RandomBytes randomBytes) {
		return serialize(MAX_SIZE, randomBytes);
	}
	
	public String toString() {
		String result = String.format("Size:%d, (%d, %d, %d, %d, %d, %d)", this.packet_size, this.train_num, this.seq_num, this.pkt_info, this.sent_timestamp, this.recv_timestamp, this.sender_id);
		return result;
	}
	
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}

