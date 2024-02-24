import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {
    private Config config;
    private ExecutorService threadPool;
    private ServerSocket serverSocket;
    private volatile boolean isRunning;

    public WebServer(String configFilePath) throws IOException {
        this.config = new Config(configFilePath);
        this.threadPool = Executors.newFixedThreadPool(config.getMaxThreads());
        this.isRunning = true;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(config.getPort());
            System.out.println("Server is listening on port " + config.getPort()+" Woohoo!");

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New Client Connected");

                    ClientHandler clientHandler = new ClientHandler(clientSocket, config);
                    threadPool.execute(clientHandler);
                } catch (IOException e) {
                    if (isRunning) {
                        System.out.println("Server accept error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            stop();
        }
    }

    public void stop() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.out.println("Error closing server socket: " + e.getMessage());
            }
        }
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
        }
    }

    public static void main(String[] args) {
        try {
            WebServer server = new WebServer("config.ini");
            server.start();
        } catch (IOException e) {
            System.out.println("Could not start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
