package work4;
import java.io.*;
import java.net.*;

public class Server {
    public static void main(String[] args) throws IOException {
        int port = 5000;  // 任意のポート番号
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server started");

        Socket clientSocket = serverSocket.accept();
        
        System.out.println("Client accepted");

        // Client.javaがwebsocket通信を完了したことを報告してきてから通信を開始する
        // 報告方法としては、Client.javaがサーバーに対して"WebSocket Connected"という文字列を送信する
        System.out.println("Waiting for WebSocket connection from client...");
        
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        String message = "";
        while (true) {
            message = in.readUTF();
            if (message.equals("WebSocket Connected")) {
                break;
            }
            // System.err.println("Invalid message from client: " + message);
            // 必要に応じてクライアントに再送要求などを送る場合はここに追加
        }
        System.out.println("WebSocket Connected");


        // クライアントにデータを送信する
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

        // stock_data.txtをリソースとして読み込む
        InputStream is = Server.class.getClassLoader().getResourceAsStream("stock_data.txt");
        if (is == null) {
            System.err.println("stock_data.txt not found in resources.");
            return;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line = "";
            line = br.readLine(); // 見出し削除
            while ((line = br.readLine()) != null) {
                out.writeUTF(line);
                try {
                    Thread.sleep(48);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // close the connection
        try {
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
            if (out != null) out.close();
        } catch (IOException i) {
            System.out.println(i);
        }
    }
}