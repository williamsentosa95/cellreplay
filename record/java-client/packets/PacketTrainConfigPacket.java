package packets;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

public class PacketTrainConfigPacket {
	final static public int LENGTH = Long.BYTES * 6;
	final static public int UDP_HEADER_SIZE = 42;
	
	public long sender_id;
	public long upload_gap_ms;
	public long download_gap_ms;
	public long upload_num_packets;
	public long download_num_packets;
	public long packet_size;
	
	public PacketTrainConfigPacket(long senderId, long uploadGapMs, long downloadGapMs, long uploadNumPackets, long downloadNumPackets, long packetSize) {
		this.sender_id = senderId;
		this.upload_gap_ms = uploadGapMs;
		this.download_gap_ms = downloadGapMs;
		this.upload_num_packets = uploadNumPackets;
		this.download_num_packets = downloadNumPackets;
		this.packet_size = packetSize;
	}
	
	public PacketTrainConfigPacket(byte[] stream) {
		ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOfRange(stream, 0, PacketTrainConfigPacket.LENGTH));
		LongBuffer longBuffer = bb.asLongBuffer();
		this.sender_id = longBuffer.get(0);
		this.upload_gap_ms = longBuffer.get(1);
		this.download_gap_ms = longBuffer.get(2);
		this.upload_num_packets = longBuffer.get(3);
		this.download_num_packets = longBuffer.get(4);
		this.packet_size = longBuffer.get(5);
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
		temp = longToBytes(upload_gap_ms);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = longToBytes(download_gap_ms);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = longToBytes(upload_num_packets);
		for (int i=0; i<temp.length; i++) {
			result[currentIdx] = temp[i];
			currentIdx += 1;
		}
		temp = longToBytes(download_num_packets);
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
		result = String.format("Sender: %d, %d %d %d %d", this.sender_id, this.upload_gap_ms, this.download_gap_ms, this.upload_num_packets, this.download_num_packets, this.packet_size);
		return result;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
	}

}
