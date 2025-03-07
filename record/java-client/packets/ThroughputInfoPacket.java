package packets;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

public class ThroughputInfoPacket {
	final public static int LENGTH = Long.BYTES * 2 + Double.BYTES;
	final static public int UDP_HEADER_SIZE = 42;
	
	public long sender_id;
	public long log_time;
	public double throughput;
	
	public ThroughputInfoPacket(long sender_id, long log_time, double throughput) {
		this.sender_id = sender_id;
		this.log_time = log_time;
		this.throughput = throughput;
	}
	
	public ThroughputInfoPacket(byte[] stream) {
		ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOfRange(stream, 0, ThroughputInfoPacket.LENGTH - Double.BYTES));
		LongBuffer longBuffer = bb.asLongBuffer();
		this.sender_id = longBuffer.get(0);
		this.log_time = longBuffer.get(1);
		bb = ByteBuffer.wrap(Arrays.copyOfRange(stream, ThroughputInfoPacket.LENGTH - Double.BYTES, ThroughputInfoPacket.LENGTH));
		DoubleBuffer doubleBuffer = bb.asDoubleBuffer();
		this.throughput = doubleBuffer.get(0);
	}
	
	private byte[] longToBytes(long input) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
	    buffer.putLong(input);
	    return buffer.array();
	}
	
	private byte[] doubleToBytes(double input) {
		ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
	    buffer.putDouble(input);
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
		temp = longToBytes(log_time);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = doubleToBytes(throughput);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
	    return result;
	}
	
	public String toString() {
		String result = "";
		result = String.format("Sender: %d, %d %f", this.sender_id, this.log_time, this.throughput);
		return result;
	}
}
