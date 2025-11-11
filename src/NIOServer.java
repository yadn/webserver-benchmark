import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;

public class NIOServer { // NIO = non blocking IO
  public static void main(String[] args) throws Exception {
    Selector selector = Selector.open(); // Selector can monitor 1000s of channels (serverSocketChannel + client socketChannel)
    ServerSocketChannel server = ServerSocketChannel.open(); //this opens a non-blocking server socketchannel.. non blocking version of ServerSocket
    // in blocking IO- ServerSocket, in NIO - ServerSocketChannel
    // in blocking IO- Socket, in NIO - SocketChannel


    server.bind(new InetSocketAddress(8080));
    server.configureBlocking(false); // make it non blocking
    System.out.println("NIOServer is listening on port 8080...");
    // Scheduler for simulating async latency without blocking threads
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      scheduler.shutdownNow();
    }));
    SelectionKey key1 = server.register(selector, SelectionKey.OP_ACCEPT); // OP_ACCEPT means OPERATION ACCEPT.. it gets fired when new client connection is waiting.. so this line will register the server socket channel with selector to listen for new client connections.. and will give Registration key object = SelectionKey

    while (true) { // infinite loop to handle events
      selector.select(); // this will block until OP_ACCEPT, OP_READ, OP_WRITE events are fired
      Set<SelectionKey> keys = selector.selectedKeys(); // gives all channels ready for IO operations
      Iterator<SelectionKey> it = keys.iterator();
      while (it.hasNext()) {
        SelectionKey key = it.next();
        if (key.isAcceptable()) { // if OP_ACCEPT i.e ready to accept new connection, then accept new connection
          ServerSocketChannel serv = (ServerSocketChannel) key.channel(); // get the server socket channel from the registration Key
          SocketChannel client = serv.accept(); // will give client SocketChannel
          client.configureBlocking(false); // this will set the client SocketChannel in non blocking mode so that reading from it will not block the thread
          client.register(selector, SelectionKey.OP_READ); // this will register the client socket channel with selector to listen for read events
        }
        if (key.isReadable()) { // channel corresponding to registration Key is ready for reading
          SocketChannel client = (SocketChannel) key.channel(); // get the client socketchannel from the key
          ByteBuffer buffer = ByteBuffer.allocate(1024); // temp byte buffer (1 KB)
          client.read(buffer); // non-blocking read (we ignore the rest for this benchmark)

          // Prepare response buffer and attach to key
          ByteBuffer resp = ByteBuffer.wrap(HttpResponse.HELLO);
          key.attach(resp);

          // Temporarily disable interest to avoid busy notifications until delay elapses
          key.interestOps(0);

          // Schedule enabling OP_WRITE after 200ms; wake up selector to apply it promptly
          scheduler.schedule(() -> {
            try {
              if (key.isValid()) {
                key.interestOps(SelectionKey.OP_WRITE);
                selector.wakeup();
              }
            } catch (CancelledKeyException ignored) {}
          }, 200, TimeUnit.MILLISECONDS);
        }

        if (key.isWritable()) { // ready to write the prepared response
          SocketChannel client = (SocketChannel) key.channel();
          ByteBuffer resp = (ByteBuffer) key.attachment();
          if (resp == null) {
            resp = ByteBuffer.wrap(HttpResponse.HELLO);
            key.attach(resp);
          }
          client.write(resp);
          if (!resp.hasRemaining()) {
            client.close();
            key.cancel();
          } else {
            // Still not fully written; keep OP_WRITE interest
            key.interestOps(SelectionKey.OP_WRITE);
          }
        }
        it.remove();
      }
    }
  }
}
