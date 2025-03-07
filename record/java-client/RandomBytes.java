import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class RandomBytes {
	final public static int MAX_PACKET_SIZE = 1500;
	public int dict_size;
	
	private ArrayList<byte[]> random_dicts;
	private int curr_idx;
	
	public RandomBytes(int size) {
		this.dict_size = size;
		Random rnd = new Random();
		random_dicts = new ArrayList<byte[]>();
		curr_idx = 0;
		for (int i =0; i<size; i++) {
			byte[] rndBytes = new byte[MAX_PACKET_SIZE];
			rnd.nextBytes(rndBytes);
			random_dicts.add(rndBytes);
		}
	}
	
	public byte[] get_random_bytes(int byte_length) {
		assert(byte_length < MAX_PACKET_SIZE);
		byte[] result = Arrays.copyOfRange(random_dicts.get(curr_idx), 0, byte_length);
		curr_idx = (curr_idx + 1) % random_dicts.size();
		return result;
	}
	
}
