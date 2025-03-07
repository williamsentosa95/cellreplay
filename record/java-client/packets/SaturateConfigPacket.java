package packets;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

public class SaturateConfigPacket {
	final static public int LENGTH = Long.BYTES * 4;
	final static public int UDP_HEADER_SIZE = 42;
	
	public long sender_id;
	public long upload_rate;
	public long download_rate;
	public long packet_size;
	
	public SaturateConfigPacket(long senderId, long upload_rate, long download_rate, long packetSize) {
		this.sender_id = senderId;
		this.upload_rate = upload_rate;
		this.download_rate = download_rate;
		this.packet_size = packetSize;
	}
	
	public SaturateConfigPacket(byte[] stream) {
		ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOfRange(stream, 0, SaturateConfigPacket.LENGTH));
		LongBuffer longBuffer = bb.asLongBuffer();
		this.sender_id = longBuffer.get(0);
		this.upload_rate = longBuffer.get(1);
		this.download_rate = longBuffer.get(2);
		this.packet_size = longBuffer.get(3);
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
		temp = longToBytes(upload_rate);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = longToBytes(download_rate);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = longToBytes(packet_size);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
	    return result;
	}
	
	public String toString() {
		String result = "";
		result = String.format("Sender: %d, %d %d %d", this.sender_id, this.upload_rate, this.download_rate, this.packet_size);
		return result;
	}

}
