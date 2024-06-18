package http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

public class HttpRequest {
    private static final Logger log = LoggerFactory.getLogger(HttpRequest.class);

    private String method;
    private String path;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> parameters = new HashMap<>();

    public HttpRequest(final InputStream in) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line = br.readLine();
            if (line == null) {
                return;
            }

            processRequestLine(line);

            line = br.readLine();
            while (line != null && !line.equals("")) {
                log.debug("header : {}", line);
                String[] tokens = line.split(":");
                headers.put(tokens[0].trim(), tokens[1].trim());
                line = br.readLine();
            }

            if ("POST".equals(method)) {
                String body = IOUtils.readData(br, Integer.parseInt(headers.get("Content-Length")));
                parameters = HttpRequestUtils.parseQueryString(body);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void processRequestLine(final String requestLine) {
        log.debug("request line : {}", requestLine);
        String[] tokens = requestLine.split(" ");
        method = tokens[0];

        if ("POST".equals(method)) {
            path = tokens[1];
            return;
        }

        int index = tokens[1].indexOf("?");
        if (index == -1) {
            path = tokens[1];
        } else {
            path = tokens[1].substring(0, index);
            parameters = HttpRequestUtils.parseQueryString(tokens[1].substring(index + 1));
        }
    }


    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getHeader(final String name) {
        return headers.get(name);
    }

    public String getParameter(final String name) {
        return parameters.get(name);
    }

    public Map<String, String> getParameters() {
        return parameters;
    }
}
