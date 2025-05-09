package Work3;
import java.io.*;
import java.net.*;

public class Server {
    public static void main(String[] args) throws IOException {
        int port = 5000;  // 任意のポート番号
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server started");

        Socket clientSocket = serverSocket.accept();
        
        System.out.println("Client accepted");

        // // takes input from the client socket
        // DataInputStream in = new DataInputStream(
        //     new BufferedInputStream(clientSocket.getInputStream()));

        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
        // PrintWriter out = new PrintWriter((clientSocket.getOutputStream()));

        // stock_data.txtからデータを読み込む
        File file = new File("stock_data.txt");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = "";
            line = br.readLine(); // 見出し削除
            while ((line = br.readLine()) != null) {
                // System.out.println(line);
                out.writeUTF(line);
                // 48ミリ秒の休止
                try {
                    Thread.sleep(48);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
            // out.println("END");
            // while((line = br.readLine()) != null) {

            // }
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
        
        // out.close();
        // clientSocket.close();
        // serverSocket.close();
    }
}


// import java.io.BufferedInputStream;
// import java.io.DataInputStream;
// import java.io.DataOutputStream;
// import java.io.IOException;
// import java.net.ServerSocket;
// import java.net.Socket;

// public class Server {
//     public static void main(String[] args) {
//         int port = 5000; // server port
//         DataInputStream input = null;
//         DataOutputStream out = null;
//         Socket socket = null;
//         ServerSocket server = null;
//         try {
//             server = new ServerSocket(port);
//             System.out.println("Server started");
//             System.out.println("Waiting for a client ...");
//             socket = server.accept();
//             System.out.println("Client accepted");
//             // takes input from the client socket
//             DataInputStream in = new DataInputStream(
//                     new BufferedInputStream(socket.getInputStream()));

//             String line = "";

//             // reads message from client until "Over" is sent
//             while (!line.equals("Over")) {
//                 try {
//                     line = in.readUTF();
//                     System.out.println(line);
//                 } catch (IOException i) {
//                     System.out.println(i);
//                 }
//             }
//         } catch (IOException i) {
//             System.out.println(i);
//         } finally {
//             // close the connection
//             if (socket != null)
//                 socket.close();
//             if (server != null)
//                 server.close();
//             if (input != null)
//                 input.close();
//             if (out != null)
//                 out.close();
//         }
//     }
// }