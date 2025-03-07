package packets;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.Random;

public class ControlPacket {
	final static public int LENGTH = Long.BYTES * 3;
	final static public int UDP_HEADER_SIZE = 42;
	
	private long sender_id;
	private long content;
	private long exp_id;
	
	final static public long SYN = 100;
	final static public long SYN_ACK = 150;
	final static public long CONFIG_ACK = 170;
	final static public long FIN = 200;
	
	public ControlPacket(long sender_id, long content) {
		this.sender_id = sender_id;
		this.content = content;
		this.exp_id = 0;
	}
	
	public ControlPacket(long sender_id, long content, long exp_id) {
		this.sender_id = sender_id;
		this.content = content;
		this.exp_id = exp_id;
	}
	
	public ControlPacket(byte[] stream) {
		ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOfRange(stream, 0, LENGTH));
		LongBuffer longBuffer = bb.asLongBuffer();
		this.sender_id = longBuffer.get(0);
		this.content = longBuffer.get(1);
		this.exp_id = longBuffer.get(2);
	}
	
	private byte[] longToBytes(long input) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
	    buffer.putLong(input);
	    return buffer.array();
	}
	
	public byte[] serialize() {
		byte[] result = new byte[LENGTH];
		int currentIdx = 0;
		// Seq_num
		byte[] temp = longToBytes(sender_id);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = longToBytes(content);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = longToBytes(exp_id);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
	    return result;
	}
	
	public String toString() {
		String result = "";
		if (content == this.SYN) {
			result = String.format("Sender: %d, exp=%d, Control packet SYN", sender_id, exp_id);
		} else if (content == this.FIN) {
			result = String.format("Sender: %d, exp=%d, Control packet FIN", sender_id, exp_id);
		} else {
			result = String.format("Sender: %d, exp=%d, Control packet UNKNOWN %d", sender_id, exp_id, content);
		}
		return result;
	}
	
	public long getContent() {
		return this.content;
	}
	
}
