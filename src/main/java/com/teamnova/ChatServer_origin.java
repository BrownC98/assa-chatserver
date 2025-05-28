// package main;
// package com.assa.chatserver;
// import java.io.BufferedReader;
// import java.io.IOException;
// import java.io.InputStreamReader;
// import java.io.PrintWriter;
// import java.net.InetSocketAddress;
// import java.net.ServerSocket;
// import java.net.Socket;
// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.CopyOnWriteArrayList;

// import com.assa.chatserver.DTO.Client;
// import com.assa.chatserver.handler.RoomCreateHandler;
// import com.sun.net.httpserver.HttpServer;

// public class ChatServer_origin {
//     private static final int PORT = 12345;
//     private static final int HTTP_PORT = 8080;
    
//     public static final Map<Long, List<Client>> rooms = new ConcurrentHashMap<>(); // 채팅방 저장하는 map
//     public static List<ChatThread> clients = new CopyOnWriteArrayList<>(); // 채팅 스레드 리스트

//     public static void main(String[] args) {
//         // HTTP 서버 시작
//         startHttpServer();

//         try (ServerSocket serverSocket = new ServerSocket(PORT)) {
//             System.out.println("채팅 서버 켜짐, 클라이언트를 기다리는 중");
//             while (true) {
//                 Socket socket = serverSocket.accept();
//                 ChatThread thread = new ChatThread(socket, clients);
//                 clients.add(thread);
//                 System.out.println("새로운 사용자 접속");
//                 System.out.println("현재 사용자 수 : " + clients.size());
//                 thread.start();
//             }
//         } catch (IOException e) {
//             e.printStackTrace();
//         }
//     }

//     // http 서버 시작
//     private static void startHttpServer() {
//         try {
//             HttpServer httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
//             // 라우팅 경로 지정 (ex: http://localhost:8080/socket/room/create)
//             httpServer.createContext("/socket/room/create", new RoomCreateHandler());
//             httpServer.setExecutor(null);
//             httpServer.start();
//             System.out.println("HTTP 서버 시작: 포트 " + HTTP_PORT);
//         } catch (Exception e) {
//             e.printStackTrace();
//         }
//     }
// }

// class ChatThread extends Thread {
//     private Socket socket;
//     private BufferedReader in;
//     private PrintWriter out;
//     private List<ChatThread> clients;

//     public ChatThread(Socket socket, List<ChatThread> clients) {
//         this.clients = clients;
//         this.socket = socket;
//         try {
//             this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//             this.out = new PrintWriter(socket.getOutputStream(), true);
//         } catch (Exception e) {
//             e.printStackTrace();
//             closeResources();
//         }
//     }

//     public void run() {
//         // 1. inputStream으로부터 메시지를 전송받는다.
//         // 2. 모든 ChatThread에 1에서 전달받은 메시지를 전달한다.
//         // 여기선 단순히 문자열 데이터를 전달만 해주는 역할을 한다.
//         String line = null;
//         try {
//             while ((line = in.readLine()) != null) {
//                 System.out.println("message: " + line);
//                 for (ChatThread thread : clients) {
//                     thread.out.println(line);
//                 }
//             }
//         } catch (IOException e) {

//         } finally {
//             System.out.println("사용자가 나갔습니다.");
//             clients.remove(this);
//             closeResources();
//         }
//     }

//     /**
//      * 리소스 close 메소드
//      */
//     private void closeResources() {
//         if (in != null) {
//             try {
//                 in.close();
//             } catch (Exception ex) {
//                 ex.printStackTrace();
//             }
//         }

//         if (out != null) {
//             out.close();
//         }

//         if (socket != null) {
//             try {
//                 socket.close();
//             } catch (Exception ex) {
//                 ex.printStackTrace();
//             }

//         }
//     }
// }
