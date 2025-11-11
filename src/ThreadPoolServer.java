import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ThreadPoolServer {
  public static void main(String[] args) throws Exception {
    int threads = args.length > 0 ? Integer.parseInt(args[0]) : 10;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    ServerSocket server = new ServerSocket(8080);
    System.out.println("Thread pool server on port 8080 (" + threads + " threads)");

    while (true) {
      Socket client = server.accept(); // main thread always listens to incoming client requests
      pool.submit(() -> { // as soon as client connects -> submit a task to the thread pool -> thread pool will internally put it in blocking queue
        try {
          BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
          OutputStream out = client.getOutputStream();

          in.readLine(); // Just read and ignore
          // Simulate downstream I/O latency (e.g., DB or RPC)
          Thread.sleep(200);
          out.write(HttpResponse.HELLO);
          out.flush();
          client.close();
        } catch (Exception e) {}
      });
    }
  }

}
