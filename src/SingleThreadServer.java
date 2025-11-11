// SingleThreadServer.java
import java.io.*;
import java.net.*;

public class SingleThreadServer {
  public static void main(String[] args) throws Exception {
    ServerSocket server = new ServerSocket(8080); // this will create a server socket on port 8080 for TCP connections
    System.out.println("Single-thread server on port 8080");

    while (true) { // infinite loop to make the server socket always listening to incoming client connections
      Socket client = server.accept(); // this will block the thread until a client connects.. meaning, execution will not go to next line, until a client is connected.. and once a client connects to 8080, it will give the client socket..
      // socket object will have getInputStream and getOutputStream method to communicate with client
      BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream())); // getInputStream will give the raw bytes coming from client.. InputStreamReader will convert it -> char stream.. BufferedReader will just wrap it and provide readLine() method so read the chars efficiently..
      OutputStream out = client.getOutputStream();

      String clientMessage = in.readLine(); // this is using BufferedReader's method
      // Simulate downstream I/O latency (e.g., DB or RPC)
      Thread.sleep(200);
      out.write(HttpResponse.HELLO); // write the response bytes array into the  buffer
      out.flush(); // send that over network
      client.close(); // close the connection for this client
    }
  }
}

/*
input: curl -v http://localhost:8080
it actually sends:

GET / HTTP/1.1
Host: localhost:8080
User-Agent: curl/8.7.1
Accept: * / *

clientMessage will be "GET / HTTP/1.1"
but we dont care about the entire http request here.. so we are not checking other lines or paths etc..
finally we send "Hello, World!" to client..

Response to client:
HTTP/1.1 200 OK
Content-Length: 13

Hello, World!
*/