public class HttpResponse {
  public static final byte[] HELLO = (
          "HTTP/1.1 200 OK\r\n" +
                  "Content-Length: 13\r\n" +
                  "\r\n" +
                  "Hello, World!"
  ).getBytes();
}
//\n = Line Feed (LF)      = Move cursor down one line
//\r = Carriage Return (CR) = Move cursor to start of line
// "HTTP/1.1 defines the sequence CR LF as the end-of-line marker" thats why we are adding the \r\n