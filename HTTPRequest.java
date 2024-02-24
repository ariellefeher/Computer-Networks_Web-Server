import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class HTTPRequest {
    private String method;
    private String path;
    private Map<String, String> parameters = new HashMap<>();
    private Map<String, String> headers = new HashMap<>();

    public HTTPRequest(BufferedReader in) throws IOException {
        parseRequest(in);
    }

    private void parseRequest(BufferedReader in) throws IOException {
        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            throw new IOException("Empty request line");
        }
        System.out.println("Request Line: " + requestLine);

        StringTokenizer tokens = new StringTokenizer(requestLine);
        if (tokens.countTokens() < 2) {
            throw new IOException("Invalid request line");
        }

        this.method = tokens.nextToken();
        String fileRequested = tokens.nextToken();

        int paramIndex = fileRequested.indexOf('?');
        if (paramIndex > -1) {
            String paramString = fileRequested.substring(paramIndex + 1);
            fileRequested = fileRequested.substring(0, paramIndex);
            parseParameters(paramString);
        }

        this.path = URLDecoder.decode(fileRequested, "UTF-8");

        parseHeaders(in);
    }

    private void parseHeaders(BufferedReader in) throws IOException {
        String headerLine;
        while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
            int separator = headerLine.indexOf(":");
            if (separator == -1) {
                throw new IOException("Invalid header line format");
            }
            String headerKey = headerLine.substring(0, separator).trim();
            String headerValue = headerLine.substring(separator + 1).trim();
            headers.put(headerKey, headerValue);
        }
    }

    private void parseParameters(String paramString) throws IOException {
        String[] pairs = paramString.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > -1) {
                String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                parameters.put(key, value);
            }
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

}