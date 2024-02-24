import java.io.IOException;
import java.io.OutputStream;

public class HTTPResponse {
    private OutputStream out;

    public HTTPResponse(OutputStream out) {
        this.out = out;
    }

    public void sendResponse(String status, String contentType, byte[] content) throws IOException {
        String responseHeader = "HTTP/1.1 " + status + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + content.length + "\r\n" +
                "\r\n";
        out.write(responseHeader.getBytes("UTF-8"));
        if (content.length > 0) {
            out.write(content);
        }
        out.flush();
    }

    public void sendErrorResponse(String statusCode, String message) throws IOException {
        String responseBody = "<html><head><title>Error " + statusCode + "</title></head><body><h1>Error " + statusCode + "</h1><p>" + message + "</p></body></html>";
        sendResponse(statusCode, "text/html; charset=UTF-8", responseBody.getBytes("UTF-8"));
    }
}
