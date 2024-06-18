package webserver;

import db.DataBase;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

import java.nio.file.Files;
import java.util.Map;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String firstLine = br.readLine();
            if (firstLine == null) {
                return;
            }

            String method = HttpRequestUtils.getMethod(firstLine);
            String url = HttpRequestUtils.getUrl(firstLine);

            if (method.equals("POST")) {
                log.debug("post entered");
                doPost(url, br, out);
            } else {
                log.debug("get entered");
                doGet(url, br, out);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void doPost(final String url, final BufferedReader br, final OutputStream out)
        throws IOException {
        //Content-Length 를 구한다
        //Content Body를 구한다.
        String line = null;
        StringBuilder sb = new StringBuilder();
        while ((line = br.readLine()) != null) {
            sb.append(line).append("\n");
            if (line.isEmpty()) {
                break;
            }
            log.debug("line : {}", line);
        }
        log.debug("read done");
        Map<String, String> headers = HttpRequestUtils.getHeader(sb.toString());
        log.debug("headers : {}", headers);
        int contentLength = Integer.parseInt(headers.get("Content-Length"));
        String contentBody = IOUtils.readData(br, contentLength);
        Map<String, String> params = HttpRequestUtils.parseQueryString(contentBody);

        User user = User.from(params);
        DataBase.addUser(user);

        log.debug("User : {}", user);
    }

    private void doGet(final String url, final BufferedReader br, final OutputStream out)
        throws IOException {
        DataOutputStream dos = new DataOutputStream(out);

        byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());

        response200Header(dos, body.length);
        responseBody(dos, body);
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
