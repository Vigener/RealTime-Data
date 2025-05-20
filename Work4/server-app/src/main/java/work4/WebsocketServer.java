package work4;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

public class WebsocketServer extends WebSocketServer {

    private static int TCP_PORT = 3000;

    private static Set<WebSocket> conns;

    public WebsocketServer() {
        super(new InetSocketAddress(TCP_PORT));
        conns = new HashSet<>();
    }

    public void broadcast(String message) {
        // 全ての接続にメッセージを送信
        for (WebSocket conn : this.connections()) {
            conn.send(message);
        }
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
        System.out.println("Message from client: " + message);
        for (WebSocket socket : conns) {
            socket.send("Message from server: " + message);
        }
    }

    // エラーが発生した場合
    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            System.err.println("Error on connection: " + conn.getRemoteSocketAddress());
            conns.remove(conn);
        }
    }
}
