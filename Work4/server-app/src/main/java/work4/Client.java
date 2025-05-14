package work4;
import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Client {
    public static void main(String[] args) throws IOException {
        // 引数から[Count window or Time window], [Window size], [Slide size]を取得
        // 例: java Client count 8 2
        // 例: java Client time 5 1

        // 引数の数を確認
        if (args.length != 3) {
            System.out.println("Usage: java Client -<count[c,Count]/time[t,Time]> <Window size> <Slide size>");
            return;
        }
        String windowTypeStr = args[0]; // Count or Time
        int windowSize = Integer.parseInt(args[1]); // ウィンドウサイズ
        int slideSize = Integer.parseInt(args[2]); // スライドサイズ
        String windowType = null;
        if (windowTypeStr.equalsIgnoreCase("-count") || windowTypeStr.equalsIgnoreCase("-c") || windowTypeStr.equalsIgnoreCase("-Count")) {
            windowType = "Count";
        } else if (windowTypeStr.equalsIgnoreCase("-time") || windowTypeStr.equalsIgnoreCase("-t") || windowTypeStr.equalsIgnoreCase("-Time")) {
            windowType = "Time";
        } else {
            System.out.println("Invalid window type option. Use '-count', '-c', '-Count', '-time', '-t', or '-Time'.");
            return;
        }
        // windowSizeとslideSizeの値が正の整数であることを確認
        if (windowSize <= 0 || slideSize <= 0) {
            System.out.println("Window size and slide size must be positive integers.");
            return;
        }
        // windowSize>slideSizeであることを確認
        if (windowSize <= slideSize) {
            System.out.println("Window size must be greater than slide size.");
            return;
        }
        System.out.println("Window Type: " + windowType);
        System.out.println("Window Size: " + windowSize);
        System.out.println("Slide Size: " + slideSize);

        // サーバーのホスト名とポート番号
        String host = "localhost"; // または接続先のIPアドレス
        int port = 5000;

        // ソケットの作成
        Socket socket = new Socket(host, port);
        System.out.println("Connected");
        System.out.println("stock, open, max, min, close, old-timestamp, new-timestamp");

        // 受信ストリームの作成
        DataInputStream in = new DataInputStream(socket.getInputStream());

        // 受け取ったタプルを格納するバッファ
        String line = "";
        ArrayList<String> temp_buffer = new ArrayList<String>();

        // フォーマットの指定
        DecimalFormat df = new DecimalFormat("#0.00"); // 数値のフォーマットを指定
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");

        // ウィンドウの初期化
        LocalTime[] window = null;

        // タプルの受信
        try {
            while (true) {
                line = in.readUTF();

                // タプルを受け取った時刻
                LocalTime time = LocalTime.now();

                String output_line = line + "," + dtf.format(time);
                temp_buffer.add(output_line);

                // 出力
                // System.out.println(output_line);

                if (windowType.equals("Count")) {
                    // ウィンドウのサイズを指定
                    int windowSizeCount = windowSize; // ウィンドウサイズ
                    int slideSizeCount = slideSize; // スライドサイズ

                    // バッファがウィンドウサイズと同じサイズになったら集計
                    if (temp_buffer.size() == windowSizeCount) {
                        // ウィンドウの終了インデックスを超えたタプルを集計
                        aggregateAndPrint(temp_buffer, df);

                        // ウィンドウの更新（Slide size分だけスライド）
                        for (int i = 0; i < slideSizeCount; i++) {
                            temp_buffer.remove(0); // スライドサイズ分だけ削除
                        }
                    }

                } else if (windowType.equals("Time")) {
                    if (window == null) {
                        // ウィンドウの開始時刻と終了時刻を指定
                        LocalTime start = time;
                        // LocalTime partition = time.plusSeconds(slideSize); // スライドサイズを加算
                        LocalTime end = start.plusSeconds(windowSize); 

                        // ウィンドウの開始時刻と終了時刻を配列に格納
                        // window = new LocalTime[] { start, partition, end };
                        window = new LocalTime[] { start, end };
                        System.out.println("Window initialized: " + dtf.format(window[0]) + " to " + dtf.format(window[1]));
                    }

                    // ウィンドウの終了時刻を超えたタプルを集計
                    if (time.isAfter(window[1])) {
                        // ウィンドウの終了時刻を超えたタプルを集計
                        aggregateAndPrint(temp_buffer, df);

                        // ウィンドウの更新（Slide size分だけスライド）
                        LocalTime newStart = window[0].plusSeconds(slideSize);
                        // LocalTime newPartition = window[1].plusSeconds(slideSize);
                        LocalTime newEnd = window[1].plusSeconds(slideSize);
                        window[0] = newStart;
                        // window[1] = newPartition;
                        window[1] = newEnd;

                        System.out.println("Window updated: " + dtf.format(window[0]) + " to " + dtf.format(window[1]));

                        // バッファの先頭を削除
                        // ウィンドウの開始時刻を超えたタプルを削除
                        while (!temp_buffer.isEmpty()) {
                            String firstRecord = temp_buffer.get(0);
                            String[] fields = firstRecord.split(",");
                            if (fields.length >= 7) { // Ensure there are enough fields (including timestamp)
                                LocalTime recordTime = LocalTime.parse(fields[6], dtf); // タイムスタンプを取得
                                if (recordTime.isBefore(window[0])) { // 更新後のウィンドウの開始時刻を超えている場合
                                    temp_buffer.remove(0); // 削除
                                } else {
                                    // ウィンドウの開始時刻を超えていない場合はループを抜ける
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    System.out.println("Invalid window type. Use 'count' or 'time'.");
                    return;
                }

            }
        } catch (EOFException e) {
            System.out.println("End of stream reached.");
        }

        in.close();
        socket.close();
    }

    private static void aggregateAndPrint(ArrayList<String> temp_buffer, DecimalFormat df) {
        // バッファが空でないことを確認
        if (temp_buffer.isEmpty()) {
            return;
        }


        // ウィンドウに含まれるタプルを表示
        System.out.println("Window records:");
        System.out.println("[");
        for (String record : temp_buffer) {
            System.out.println(record);
        }
        System.out.println("]");
        System.out.println("------------------------------");
        System.out.println("aggregation result:");

        // 集計結果を表示
        // ウィンドウ内に含まれる株すべてについて株ごとに集計を行う
        java.util.Map<String, ArrayList<Double>> stockCloseMap = new java.util.HashMap<>();

        for (String record : temp_buffer) {
            String[] fields = record.split(",");
            if (fields.length >= 5) {
                String stock = fields[0].trim();
                double close = Double.parseDouble(fields[4].trim());
                stockCloseMap.computeIfAbsent(stock, k -> new ArrayList<>()).add(close);
            }
        }

        // 株ごとに集計して出力(AからZの順にソート)
        java.util.List<String> stockList = new java.util.ArrayList<>(stockCloseMap.keySet());
        java.util.Collections.sort(stockList);

        for (String stock : stockList) {
            ArrayList<Double> closes = stockCloseMap.get(stock);
            int n = closes.size();
            double sum = 0.0;
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            for (double v : closes) {
                sum += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            double ave = sum / n;

            // 標準偏差計算（不偏分散）
            double std = 0.0;
            if (n > 1) {
                double sqSum = 0.0;
                for (double v : closes) {
                    sqSum += Math.pow(v - ave, 2);
                }
                std = Math.sqrt(sqSum / (n - 1));
            }

            
            System.out.printf("%s, Ave: %s, Min: %s, Max: %s, Std: %s%n",
                    stock,
                    df.format(ave),
                    df.format(min),
                    df.format(max),
                    df.format(std)
            );
        }
        System.out.println(temp_buffer.size() + " records in the buffer.");
        System.out.println("------------------------------");
        
    }
}

public class WebsocketServer extends WebSocketServer {

    private static int TCP_PORT = 3000;

    private static Set<WebSocket> conns;

    public WebsocketServer() {
        super(new InetSocketAddress(TCP_PORT));
        conns = new HashSet<>();
    }
    // 通信が接続された場合
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        conns.add(conn);
        conn.send("hello!!");
    }
    
    //通信が切断された場合
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        conns.remove(conn);
    }

    // メッセージを受け取った場合
    @Override
    public void onMessage(WebSocket conn, String message) {
        
    }
}