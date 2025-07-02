package io.github.vgnri.sink;

import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;

import io.github.vgnri.server.WebsocketServer;

public class WebSocketSink extends RichSinkFunction<String> {
    private static final long serialVersionUID = 1L;
    
    private final transient WebsocketServer wsServer;
    private final int wsPort;

    public WebSocketSink(WebsocketServer wsServer) {
        this.wsServer = wsServer;
        this.wsPort = wsServer != null ? wsServer.getPort() : 3000;
    }

    @Override
    public void invoke(String value, Context context) throws Exception {
        if (wsServer != null && value != null && !value.isEmpty()) {
            try {
                wsServer.broadcast(value);
                System.out.println("WebSocketに送信: " + value);
            } catch (Exception e) {
                System.err.println("WebSocket送信エラー: " + e.getMessage());
                // エラーが発生してもジョブを停止しない
            }
        }
    }
}
