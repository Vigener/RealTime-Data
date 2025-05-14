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