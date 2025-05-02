package Work2.MultiThreadsVer;
import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Client {
    public static void main(String[] args) throws IOException {
        String host = "localhost"; // または接続先のIPアドレス
        int port = 5000;

        Socket socket = new Socket(host, port);
        System.out.println("Connected");

        DataInputStream in = new DataInputStream(socket.getInputStream());

        String line = "";
        ArrayList<String> temp_buffer = new ArrayList<String>();
        LocalTime[] window = null;

        DecimalFormat df = new DecimalFormat("#.00"); // 数値のフォーマットを指定
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");

        try {
            while (true) {
                line = in.readUTF();

                // タプルを受け取った時刻
                LocalTime time = LocalTime.now();

                String output_line = line + "," + dtf.format(time);
                temp_buffer.add(output_line);

                // 出力
                System.out.println(output_line);

                // endを遅らせる値を変数に格納
                // 5100ms
                long delay = 5100L; // 5100ms
                
                // 初期ウィンドウの用意
                if (window == null) {
                    LocalTime begin = time;
                    LocalTime end = time.plusNanos(delay * 1_000_000L);
                    window = new LocalTime[] { begin, end };
                }


                // 最新のタプルを受け取った時刻がendを超えていたら、そこまでに受け取ったタプルの集計に移る
                if (time.isAfter(window[1])) {
                    // 最大最小の計算
                    // 受け取ったタプルの集計
                    // open_max: temp_bufferのすべての要素のopenの最大値
                    double open_max = Double.MIN_VALUE;
                    double open_min = Double.MAX_VALUE;
                    double high_max = Double.MIN_VALUE;
                    double high_min = Double.MAX_VALUE;
                    double low_max = Double.MIN_VALUE;
                    double low_min = Double.MAX_VALUE;
                    double close_max = Double.MIN_VALUE;
                    double close_min = Double.MAX_VALUE;
                    for (String record : temp_buffer) {
                        String[] fields = record.split(",");
                        if (fields.length >= 6) { // Ensure there are enough fields (including timestamp)
                            double open = Double.parseDouble(fields[1].trim()); // Parse and trim to avoid whitespace issues
                            double high = Double.parseDouble(fields[2].trim());
                            double low = Double.parseDouble(fields[3].trim());
                            double close = Double.parseDouble(fields[4].trim());

                            // 最大値の更新
                            if (open > open_max) open_max = open;
                            if (high > high_max) high_max = high;
                            if (low > low_max) low_max = low;
                            if (close > close_max) close_max = close;

                            // 最小値の更新
                            if (open < open_min) open_min = open;
                            if (high < high_min) high_min = high;
                            if (low < low_min) low_min = low;
                            if (close < close_min) close_min = close;
                        }
                    }

                    // 出力形式
                    // open_max, open_min, high_max, high_min, low_max, low_min, close_max, close_min
                    String record = String.format("------------------------------\nopen_Max: %s, open_min: %s, \nhigh_Max: %s, high_min: %s, \nlow_Max: %s, low_min: %s, \nclose_Max: %s, close_min: %s\n------------------------------",
                            df.format(open_max),
                            df.format(open_min),
                            df.format(high_max),
                            df.format(high_min),
                            df.format(low_max),
                            df.format(low_min),
                            df.format(close_max),
                            df.format(close_min)
                    );
                    System.out.println(record);
                    System.out.println(temp_buffer.size() + " records in the buffer.");

                    // もとのバッファの初期化
                    temp_buffer.clear();

                    // ウィンドウの更新
                    LocalTime newBegin = window[1]; // endの値をbeginにする
                    window[1] = newBegin.plusNanos(delay * 1_000_000L); // endを更新
                    window[0] = newBegin; // update begin with the new value
                    
                }
            }
        } catch (EOFException e) {
            System.out.println("End of stream reached.");
        }

        in.close();
        socket.close();
    }
}