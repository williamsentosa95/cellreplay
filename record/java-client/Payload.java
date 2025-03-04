import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.Random;

public class Payload {

	final static public int MAX_SIZE = 1400;
	final static public int UDP_HEADER_SIZE = 42;
	
	public long seq_num;
	public long sent_timestamp;
	public long recv_timestamp;
	public long sender_id;
	public int packet_size; // Packet size
	
	public Payload(byte[] stream, int length) {
		this.packet_size = length + UDP_HEADER_SIZE;
		ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOfRange(stream, 0, 32));
		LongBuffer longBuffer = bb.asLongBuffer();
		this.seq_num = longBuffer.get(0);
		this.sent_timestamp = longBuffer.get(1);
		this.recv_timestamp = longBuffer.get(2);
		this.sender_id = longBuffer.get(3);
	}
	
	public Payload() {
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
		// Seq_num
		temp = longToBytes(sent_timestamp);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		// Seq_num
		temp = longToBytes(recv_timestamp);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		// Seq_num
		temp = longToBytes(sender_id);
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
		String result = String.format("Size:%d, (%d, %d, %d, %d)", this.packet_size, this.seq_num, this.sent_timestamp, this.recv_timestamp, this.sender_id);
		return result;
	}
	
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
