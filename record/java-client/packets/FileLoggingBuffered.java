package packets;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileLoggingBuffered {
	private BufferedWriter writer;
	
    public FileLoggingBuffered(String base_dir, String exp_type, String file_name_prefix, long expId, long senderId, String expCond) throws IOException {
    	DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM-dd-yyyy");
        LocalDateTime now = LocalDateTime.now();
    	// Create folder with base_dir/MM-dd-yyyy/expId/exp_type
        String date_dir_name = base_dir + "/" + String.format("%s", dtf.format(now));
        File date_dir = new File(date_dir_name);
        if (!date_dir.exists()) {
            boolean result = date_dir.mkdir();
        }
        String exp_dir_name = date_dir_name;
        if (expId <= 0) {
        	exp_dir_name = date_dir_name + "/default";
        } else {
        	if (expCond != "") {
        		exp_dir_name = date_dir_name + "/" + expCond + "_" + expId;
        	} else {
        		exp_dir_name = date_dir_name + "/default_" + expId;
        	}
        }
        File exp_dir = new File(exp_dir_name);
        if (!exp_dir.exists()) {
        	boolean result = exp_dir.mkdir();
        }        
        String trace_dir_name = exp_dir + "/" + exp_type;
        File trace_dir = new File(trace_dir_name);
        if (!trace_dir.exists()) {
            boolean result = trace_dir.mkdir();
        }
        dtf = DateTimeFormatter.ofPattern("HH-mm-ss");
        String log_file_name = trace_dir + "/" + file_name_prefix + String.format("_%s_%d.txt", dtf.format(now), senderId);
        Path outputFile = Paths.get(log_file_name);
        this.writer = Files.newBufferedWriter(outputFile, Charset.defaultCharset());
    }

    public void write(String line) throws IOException {
        this.writer.append(line);
    }

    public void flush() throws IOException {
        this.writer.flush();
    }

    public void close() throws IOException {
        this.writer.close();
    }
}
