import java.io.*;
import java.net.*;
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

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");

        try {
            while (true) {
                line = in.readUTF();

                // タプルを受け取った時刻
                LocalTime time = LocalTime.now();

                String output_line = line + "," + dtf.format(time);
                temp_buffer.add(output_line);

                // 初期ウィンドウの用意
                if (window == null) {
                    LocalTime begin = time;
                    LocalTime end = time.plusNanos(5100 * 1_000_000L);
                    window = new LocalTime[] { begin, end };
                }

                // 最新のタプルを受け取った時刻がendを超えていたら、そこまでに受け取ったタプルの集計に移る
                if (time.isAfter(window[1])) {
                    // // コピーの作成
                    // ArrayList<String> temp_buffer_copy = new ArrayList<>(temp_buffer);

                    // 最大最小の計算
                    System.out.println(temp_buffer.size());

                    // もとのバッファの初期化
                    temp_buffer.clear();

                    // ウィンドウの更新
                    window[1] = window[0]; // begin = end
                    window[0] = window[1].plusNanos(5100 * 1_000_000L); // end = begin + 5100ms
                    
                }

                // 出力
                // System.out.println(output_line);
            }
        } catch (EOFException e) {
            System.out.println("End of stream reached.");
        }

        in.close();
        socket.close();
    }
}