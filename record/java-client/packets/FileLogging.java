package packets;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileLogging {

    private FileWriter fileStream;

    public FileLogging(String base_dir, String exp_name, String file_name_prefix) throws IOException {
        String dir_name = base_dir + "/" + exp_name;
        File trace_dir = new File(dir_name);
        if (!trace_dir.exists()) {
            boolean result = trace_dir.mkdir();
        }
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM-dd-yyyy");
        LocalDateTime now = LocalDateTime.now();
        String trace_date_name = trace_dir + "/" + String.format("%s", dtf.format(now));
        File trace_date_dir = new File(trace_date_name);
        if (!trace_date_dir.exists()) {
            boolean result = trace_date_dir.mkdir();
        }
        dtf = DateTimeFormatter.ofPattern("HH-mm-ss");
        String log_file_name = trace_date_dir + "/" + file_name_prefix + String.format("_%s.txt", dtf.format(now));
        this.fileStream = new FileWriter(log_file_name);
    }

    public void write(String line) throws IOException {
        this.fileStream.append(line);
    }

    public void flush() throws IOException {
        this.fileStream.flush();
    }

    public void close() throws IOException {
        this.fileStream.close();
    }


}
