package matching;

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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class Server {
    Selector selector;
    ServerSocketChannel serverSocketChannel;
    List<Client> connections = new Vector<Client>();

    public void startServer(){
        try{
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false); // 넌블로킹으로 설정
            serverSocketChannel.bind(new InetSocketAddress(5002));
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

                   // 대기중인 인원이 2명 이상이라면 매칭시켜준다.
                   while(connections.size() > 1){
                        // 원본 리스트에서 2개씩 부분 리스트를 만들어서 매칭
                        List<Client> subList = connections.subList(0,2);
                        String message = jsonAction.connectAddress();
                        Iterator<Client> subListIterator = subList.iterator();
                        while(subListIterator.hasNext()){
                            Client client = subListIterator.next();
                            client.sendData = message;
                            SelectionKey key = client.socketChannel.keyFor(selector);
                            key.interestOps(SelectionKey.OP_WRITE); // 작업 유형 변경
                        }
                        // 매칭시켜준뒤 대기 리스트에서 삭제
                        Iterator<Client> clientIterator = connections.iterator();
                        for(int i=0 ;i< 2; i++){
                            clientIterator.next();
                            clientIterator.remove();
                        }
                        System.out.println("그룹 매칭 성공");
                        selector.wakeup();
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

                // 데이터를 받아 처리한다.
                JSONParser jsonParser = new JSONParser();
                JSONObject result = (JSONObject)jsonParser.parse(data);
                String type = (String)result.get("type");
                JSONObject payload = (JSONObject) result.get("payload");
                if(type.equals("CONNECT_BREAK")){
                    System.out.println("연결 해제: "+ socketChannel.getRemoteAddress());
                    connections.remove(this);
                    socketChannel.close();
                }

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
    }

}
