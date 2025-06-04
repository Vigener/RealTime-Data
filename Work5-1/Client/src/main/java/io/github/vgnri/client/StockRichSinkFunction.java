package io.github.vgnri.client;

import org.apache.flink.configuration.Configuration; // 正しいインポート
import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;

public class StockRichSinkFunction extends RichSinkFunction<String> {
    private final String host;
    private final int port;
    private StockWebSocketServer server;

    public StockRichSinkFunction(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void open(Configuration parameters) throws Exception {
        // WebSocketサーバーのインスタンスを取得
        server = StockWebSocketServer.getInstance(host, port);
        System.out.println("StockRichSinkFunction initialized with WebSocket server");
    }

    @Override
    public void invoke(String value, Context context) throws Exception {
        if (server != null) {
            // WebSocketサーバー経由で全クライアントにブロードキャスト
            server.broadcast(value);
            System.out.println("Broadcasted to " + server.getConnectionCount() + " clients: " + 
                             (value.length() > 100 ? value.substring(0, 100) + "..." : value));
        } else {
            System.err.println("WebSocket server is not available");
        }
    }

    @Override
    public void close() throws Exception {
        super.close();
        // サーバーは他のタスクでも使用される可能性があるため、ここでは停止しない
        System.out.println("StockRichSinkFunction closed");
    }
}
