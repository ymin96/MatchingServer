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
            JsonAction jsonAction = new JsonAction();
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

                   while(connections.size() > 3){
                        List<Client> subList = connections.subList(0,4);
                        String address = ((InetSocketAddress)subList.get(0).socketChannel.getRemoteAddress()).getHostName();
                        String hostMessage = jsonAction.connectAddress(address, "host");
                        String clientMessage = jsonAction.connectAddress(address, "client");

                        for(int i=0 ;i< 4; i++){
                            subList.get(i).sendData = (i==0)?hostMessage:clientMessage;
                            SelectionKey key = subList.get(i).socketChannel.keyFor(selector);
                            key.interestOps(SelectionKey.OP_WRITE); // 작업 유형 변경
                            connections.remove(0);
                        }
                        System.out.println("그룹 매칭 성공");
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
                ByteBuffer byteBuffer = ByteBuffer.allocate(100);

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

                // 모든 클라이언트에게 문자열을 전송하는 코드
                for(Client client: connections){
                    client.sendData = data;
                    SelectionKey key = client.socketChannel.keyFor(selector);
                    key.interestOps(SelectionKey.OP_WRITE); // 작업 유형 변경
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
