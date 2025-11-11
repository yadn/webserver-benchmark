import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class VirtualThreadPoolServer {
  public static void main(String[] args) throws Exception {
    ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    ServerSocket server = new ServerSocket(8080);
    System.out.println("Virtual-thread server on port 8080...");

    while (true) {
      Socket client = server.accept(); // main thread accepts connections
      pool.submit(() -> {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             OutputStream out = client.getOutputStream()) {

          in.readLine(); // read request (blocking)

          // simulate downstream I/O
          Thread.sleep(200);

          out.write(HttpResponse.HELLO);
          out.flush();
        } catch (Exception e) {

        } finally {
          try { client.close(); } catch (IOException ignored) {}
        }
      });
    }
  }
}
