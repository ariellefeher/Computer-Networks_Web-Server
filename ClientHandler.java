import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class ClientHandler implements Runnable {
    private Socket socket;
    private Config config;
    private BufferedReader in;
    private OutputStream out;

    public ClientHandler(Socket socket, Config config) {
        this.socket = socket;
        this.config = config;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = socket.getOutputStream();
            processRequest();
        } catch (Exception e) {
            System.out.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
            try {
                sendErrorResponse("500 Internal Server Error", "An unexpected error occurred.");
            } catch (IOException ex) {
                System.out.println("Failed to send error response: " + ex.getMessage());
                ex.printStackTrace();
            }
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.out.println("Error closing streams or socket: " + e.getMessage());
            }
        }
    }
    private void processRequest() throws IOException{
        // Read the request line
        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            sendErrorResponse("400 Bad Request", "Empty request line");
            return;
        }
        System.out.println("Request Line: " + requestLine);
        String[] requestParts = requestLine.split(" ");
        if (requestParts.length > 1) {
            System.out.println("Requested path: " + requestParts[1]);
        }

        StringTokenizer tokens = new StringTokenizer(requestLine);
        if (tokens.countTokens() < 2) {
            sendErrorResponse("400 Bad Request", "Malformed request line");
            return;
        }

        /**Step 1: Parsing the Method and the Path **/
        String method = tokens.nextToken();
        String fileRequested = tokens.nextToken();

        /**Step 2: Parsing Parameters ( If they exist) **/
        Map<String, String> parameters = new HashMap<>();
        if (method.equals("GET") || method.equals("POST")) {
            int paramIndex = fileRequested.indexOf('?');
            if (paramIndex > -1) {
                String paramString = fileRequested.substring(paramIndex + 1);
                fileRequested = fileRequested.substring(0, paramIndex);
                String[] pairs = paramString.split("&");

                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    String key = URLDecoder.decode(keyValue[0], "UTF-8");
                    String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8") : "";
                    parameters.put(key, value);
                }
            }
        }
        /**Step 3: Parsing Headers **/
        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
            int separator = headerLine.indexOf(":");
            if (separator == -1) {
                throw new IOException("Invalid header line format");
            }
            String headerKey = headerLine.substring(0, separator).trim();
            String headerValue = headerLine.substring(separator + 1).trim();
            headers.put(headerKey, headerValue);

            System.out.println(headerKey + ": " + headerValue);
        }
        System.out.println(); // printing an empty line for clarity

        if (isDirectoryTraversal(fileRequested)) {
            sendErrorResponse("403 Forbidden", "Forbidden: Directory traversal attempt detected.");
            return;
        }

        switch (method) {
            case "GET":
                handleGetRequest(fileRequested, headers, parameters, true);
                break;

            case "POST":
                handlePostRequest(fileRequested, headers, parameters);
                break;

            case "HEAD":
                handleGetRequest(fileRequested, headers, parameters, false);
                break;

            case "TRACE":
                handleTraceRequest(requestLine, headers);
                break;

            default:
                sendErrorResponse("501 Not Implemented", "Method Not Implemented.");
                break;
        }
    }
    private void handleGetRequest(String fileRequested, Map<String, String> headers, Map<String, String> parameters, boolean sendBody) throws IOException {
        try {
            if (fileRequested.endsWith("/")) {
                fileRequested += config.getDefaultPage();
            }

            File file = new File(config.getRootDirectory(), fileRequested);

            if (!file.exists()) {
                sendErrorResponse("404 Not Found", "The requested file was not found on this server.");
                return;
            }

            byte[] content = Files.readAllBytes(Paths.get(file.getPath()));
            String contentType = getContentType(fileRequested);
            if (sendBody) {
                sendResponse("200 OK", contentType, content, headers);
            } else {
                sendResponse("200 OK", contentType, new byte[0], headers);
            }
        } catch (Exception e) {
            System.out.println("Error in GET request handling: " + e.getMessage());
            try {
                sendInternalServerError(e);
            } catch (IOException ex) {
                System.out.println("Failed to send internal server error response: " + ex.getMessage());
            }
        }
    }

    private void handlePostRequest(String fileRequested, Map<String, String> headers, Map<String, String> parameters) throws IOException {
        try {
            String requestBody;
            if (headers.containsKey("Content-Length")) {
                int contentLength;
                try {
                    contentLength = Integer.parseInt(headers.get("Content-Length"));
                } catch (NumberFormatException e) {
                    sendErrorResponse("400 Bad Request", "Invalid Content-Length value");
                    return;
                }
                if (contentLength < 0) {
                    sendErrorResponse("400 Bad Request", "Negative Content-Length value");
                    return;
                }

                requestBody = readFixedLengthRequestBody(in, contentLength);

            } else {

                sendErrorResponse("411 Length Required", "Content-Length header is required for POST request.");
                return;
            }

            if ("/params_info.html".equals(fileRequested)) {
                String[] postData = requestBody.split("&");
                for (String pair : postData) {
                    String[] keyValue = pair.split("=");
                    String key = URLDecoder.decode(keyValue[0], "UTF-8");
                    String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8").replace("+", " ") : "";
                    parameters.put(key, value);
                }

                StringBuilder responseContent = new StringBuilder("<html><body>");
                responseContent.append("<h1>Submitted Parameters</h1>");
                for (Map.Entry<String, String> entry : parameters.entrySet()) {
                    responseContent.append("<p>").append(entry.getKey()).append(": ")
                            .append(entry.getValue()).append("</p>");
                }
                responseContent.append("</body></html>");
                sendResponse("200 OK", "text/html", responseContent.toString().getBytes("UTF-8"), headers);
            } else {
                handleGetRequest(fileRequested, headers, parameters, true);
            }
        }catch(Exception e){
            System.out.println("Error in POST request handling: " + e.getMessage());
            try {
                sendInternalServerError(e);
            } catch (IOException ex) {
                System.out.println("Failed to send internal server error response: " + ex.getMessage());
            }
        }
    }

    private void handleTraceRequest(String requestLine, Map<String, String> headers) throws IOException {
        try {
        StringBuilder responseContent = new StringBuilder("TRACE Request Received:\r\n");
        responseContent.append(requestLine).append("\r\n");

        for (Map.Entry<String, String> header : headers.entrySet()) {
            responseContent.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        sendResponse("200 OK", "message/http", responseContent.toString().getBytes("UTF-8"), headers);

    } catch (IOException e) {
        try {
            sendErrorResponse("500 Internal Server Error", "Error processing TRACE request");
        } catch (IOException ex) {
            System.out.println("Failed to send internal server error response for TRACE request: " + ex.getMessage());
        }
    }
}

    private String readFixedLengthRequestBody(BufferedReader in, int contentLength) throws IOException {
        StringBuilder requestBody = new StringBuilder(contentLength);
        int read;
        int totalRead = 0;
        char[] buffer = new char[1024];
        while (totalRead < contentLength) {
            read = in.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead));
            if (read == -1) { // EOF reached before reading the expected length
                throw new IOException("Unexpected end of stream");
            }
            requestBody.append(buffer, 0, read);
            totalRead += read;
        }
        return requestBody.toString();
    }

    private void sendErrorResponse(String statusCode, String message) throws IOException {
        String responseBody = "<html><head><title>Error " + statusCode + "</title></head><body><h1>Error " + statusCode + "</h1><p>" + message + "</p></body></html>";
        int contentLength = responseBody.getBytes("UTF-8").length;
    
        String response = "HTTP/1.1 " + statusCode + "\r\n" +
                          "Content-Type: text/html; charset=UTF-8\r\n" +
                          "Content-Length: " + contentLength + "\r\n" +
                          "Connection: close\r\n\r\n" +
                          responseBody;
    
        out.write(response.getBytes("UTF-8"));
        System.out.println("Response: " + statusCode + " - " + message);
    }
    

    private String getContentType(String fileRequested) {
        if (fileRequested.endsWith(".html") || fileRequested.endsWith(".htm")) {
            return "text/html";
        } else if (fileRequested.endsWith(".jpg") || fileRequested.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileRequested.endsWith(".png")) {
            return "image/png";
        } else if (fileRequested.endsWith(".gif")) {
            return "image/gif";
        } else if (fileRequested.endsWith(".bmp")) {
            return "image/bmp";
        } else if (fileRequested.endsWith(".ico")) {
            return "icon";
        } else {
            return "application/octet-stream";
        }
    }

    private void sendResponse(String status, String contentType, byte[] content, Map<String, String> headers) throws IOException {
        boolean clientRequestedChunked = "yes".equalsIgnoreCase(headers.get("chunked")) || "Chunked".equalsIgnoreCase(headers.get("Transfer-Encoding"));
        String responseHeader;
        if (clientRequestedChunked) {
            // Start line and headers for a chunked response
            String startLine = "HTTP/1.1 " + status + "\r\n";
            String headerLines = "Content-Type: " + contentType + "\r\nTransfer-Encoding: chunked\r\n\r\n";

            // Logging the headers
            System.out.println(startLine + headerLines.trim().replace("\r\n", "\n"));
            out.write((startLine + headerLines).getBytes(StandardCharsets.UTF_8));

            int offset = 0;
            int bufferSize = 1024;
            while (offset < content.length) {
                int chunkSize = Math.min(bufferSize, content.length - offset);
                String chunkHeader = Integer.toHexString(chunkSize) + "\r\n";
                out.write(chunkHeader.getBytes(StandardCharsets.UTF_8));
                out.write(content, offset, chunkSize);
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));

                offset += chunkSize;
            }
            String endOfChunkedResponse = "0\r\n\r\n";
            out.write(endOfChunkedResponse.getBytes());
        } else {
            responseHeader = "HTTP/1.1 " + status + "\r\n" +
                    "Content-Type: " + contentType + "\r\n" +
                    "Content-Length: " + content.length + "\r\n" +
                    "\r\n";
            out.write(responseHeader.getBytes());
            if (content.length > 0) {
                out.write(content);
            }
             System.out.println("Response: " + responseHeader + "\r\n");
        }
        out.flush();
    }

    private boolean isDirectoryTraversal(String fileRequested){
       if(fileRequested.contains("../") || fileRequested.contains("..\\") || !fileRequested.startsWith("/") ||
               fileRequested.contains("%2E%2E%2F") || fileRequested.contains("%2E%2E/") || fileRequested.contains("..%2F")) {
           return true;
       }

       return false;
    }

    private void sendInternalServerError(Exception e) throws IOException {
        e.printStackTrace();
        sendErrorResponse("500 Internal Server Error", "The Server Encountered an Unexpected Error.");
    }
}
