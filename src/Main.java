import game.GameServer;
import matching.Server;

public class Main {
    public static void main(String[] argv){
        Server server = new Server();
        server.startServer();
        GameServer gameServer = new GameServer();
        gameServer.startServer();
    }
}
