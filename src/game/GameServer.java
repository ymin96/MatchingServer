package game;

import matching.ActionCreator;
import matching.Server;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    private List<Client> connections = new Vector<Client>();
    private Map<String, List<Client>> userMap = new ConcurrentHashMap<>();
    private game.ActionCreator actionCreator = new game.ActionCreator();

    // 액션 객체의 결과 상태를 구분하기 위한 변수
    public static final int SUCCESS = 10;
    public static final int MATCH_FAIL = 11;
    public static final int TIME_OUT = 12;
    public static final int SEARCH_FAIL = 13;

    public void startServer(){
        try{
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false); // 넌블로킹으로 설정
            serverSocketChannel.bind(new InetSocketAddress(5003));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        }catch (Exception e){
            if(serverSocketChannel.isOpen()){
                stopServer();
            }
            return;
        }

        Thread thread = new Thread(() -> {
            ActionCreator jsonAction = new ActionCreator();
            while(true){
                try {
                    int keyCount = selector.select();

                    if(keyCount == 0)
                        continue;

                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectedKeys.iterator();
                    while(iterator.hasNext()){
                        SelectionKey selectionKey = iterator.next();
                        if(selectionKey.isAcceptable()){
                            accept(selectionKey);
                        }
                        else if(selectionKey.isReadable()){
                            Client client = (Client)selectionKey.attachment();
                            client.receive(selectionKey);
                        }
                        else if(selectionKey.isWritable()){
                            Client client = (Client) selectionKey.attachment();
                            client.send(selectionKey);
                        }
                        iterator.remove();
                    }


                } catch (IOException e) {
                    if (serverSocketChannel.isOpen()) {
                        stopServer();
                    }
                    break;
                }
            }
        });
        thread.start();
    }

    public void stopServer(){
        try {
            Iterator<Client> iterator = connections.iterator();
            while(iterator.hasNext()){
                Client client = iterator.next();
                client.socketChannel.close();
                iterator.remove();
            }
            if(serverSocketChannel != null && serverSocketChannel.isOpen()){
                serverSocketChannel.close();
            }
            if(selector != null && selector.isOpen()){
                selector.close();
            }
        }catch (Exception e){ }
    }

    void accept(SelectionKey selectionKey){
        try{
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
            SocketChannel socketChannel = serverSocketChannel.accept();

            System.out.println("연결 수락: "+socketChannel.getRemoteAddress());

            Client client = new Client(socketChannel);
            connections.add(client);
        }catch (Exception e){
            if(serverSocketChannel.isOpen()){stopServer();}
        }
    }


    public class Client {
        SocketChannel socketChannel;
        String sendData;
        boolean turn = false;

        Client(SocketChannel socketChannel) throws IOException {
            this.socketChannel = socketChannel;
            socketChannel.configureBlocking(false);
            SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
            selectionKey.attach(this);
        }

        void receive(SelectionKey selectionKey){
            try{
                ByteBuffer byteBuffer = ByteBuffer.allocate(1000);

                // 상대방이 비정상 종료를 했을 경우 자동 IOException 발생
                int byteCount = socketChannel.read(byteBuffer); // 데이터 받기

                // 상대방이 SocketChannel의 close() 메소드를 호출할 경우
                if(byteCount == -1){
                    throw new IOException();
                }

                // 문자열 변환
                byteBuffer.flip();
                Charset charset = Charset.forName("UTF-8");
                String data = charset.decode(byteBuffer).toString();

                System.out.println("receive: " + data);

                // 데이터를 받아 리듀서 함수에서 처리한다.
                JSONParser jsonParser = new JSONParser();
                JSONObject result = (JSONObject)jsonParser.parse(data);
                reducer(result);

                selector.wakeup();
            } catch (Exception e){
                try{
                    connections.remove(this);
                    socketChannel.close();
                } catch (IOException e2){}
            }
        }

        void send(SelectionKey selectionKey){
            try{
                System.out.println("send: " + sendData);

                Charset charset = Charset.forName("UTF-8");
                ByteBuffer byteBuffer = charset.encode(sendData);
                socketChannel.write(byteBuffer);
                selectionKey.interestOps(SelectionKey.OP_READ);
                selector.wakeup();
            }catch (Exception e){
                try{
                    connections.remove(this);
                    socketChannel.close();
                } catch (IOException e2){}
            }
        }

        void reducer(JSONObject result) {
            String type = (String)result.get("type");
            JSONObject payload = (JSONObject) result.get("payload");
            switch (type){
                // 연결 요청 메시지
                case "CONNECT_ROOM": {
                    String uuid = (String) payload.get("uuid");
                    // 전달받은 uuid로 생성된 방이 없다면 새로 만들고 있다면 Map 에서 가져온다.
                    ArrayList<Client> clients = (userMap.containsKey(uuid)) ? (ArrayList<Client>) userMap.get(uuid) : new ArrayList<>();
                    clients.add(this);
                    userMap.put(uuid, clients);

                    // 2명이 모이면 준비 완료 신호를 보내준다.
                    if (clients.size() == 2) {
                        clients.get((int) (Math.random() * 2)).turn = true;
                        for (Client client : clients) {
                            String data = actionCreator.readyStatusFinish(uuid, client.turn); // type:"READY_STATUS_FINISH"
                            client.sendData = data;
                            SelectionKey key = client.socketChannel.keyFor(selector);
                            key.interestOps(SelectionKey.OP_WRITE); // 작업 유형 변경
                        }
                        selector.wakeup();
                    }
                    break;
                }
                // 액션 객체 전달
                case "SEND_ACTION": {
                    String uuid = (String) payload.get("uuid");
                    JSONObject action = (JSONObject) payload.get("action");
                    ArrayList<Client> clients = (ArrayList<Client>) userMap.get(uuid);
                    int resultType = Math.toIntExact((Long)action.get("return_type"));
                    // 결과 타입에 따라 게임을 이어갈지 끝낼지 결정해준다.
                    if(resultType == SUCCESS){
                        // 턴을 바꿔서 클라이언트에게 재전송
                        for(Client client: clients){
                            client.turn = !client.turn;
                            String data = actionCreator.receiveAction(uuid, action, client.turn); // type:"RECEIVE_ACTION"
                            client.sendData = data;
                            SelectionKey key = client.socketChannel.keyFor(selector);
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                    }
                    else{
                        // Winner 정보를 추가해서 클라이언트에게 재전송
                        for(Client client: clients){
                            boolean win = (!client.equals(this));
                            String data = actionCreator.receiveFailAction(uuid, action, win); // type:"RECEIVE_FAIL_ACTION"
                            client.sendData = data;
                            SelectionKey key = client.socketChannel.keyFor(selector);
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                    }
                    selector.wakeup();
                    break;
                }
                // 연결 해제 메시지
                case "CONNECT_CLOSE": {
                    String uuid = (String) payload.get("uuid");
                    ArrayList<Client> clients = (ArrayList<Client>) userMap.get(uuid);
                    Iterator<Client> iterator = clients.iterator();
                    try {
                        while (iterator.hasNext()) {
                            Client client = iterator.next();
                            connections.remove(client);
                            client.socketChannel.close();
                        }
                    } catch (IOException e) {
                    }
                    userMap.remove(uuid);
                    break;
                }
            }
        }
    }

}
