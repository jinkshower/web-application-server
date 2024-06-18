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
import java.util.Collection;
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
            log.debug("method : {}, url : {}", method, url);

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
        if (url.equals("/user/create")) {
            createUser(br, out);
        } else if (url.equals("/user/login")) {
            login(br, out);
        }
    }

    private void doGet(final String url, final BufferedReader br, final OutputStream out)
        throws IOException {
        if (url.equals("/user/list")) {
            getAllUsers(br, out);
            return;
        }
        DataOutputStream dos = new DataOutputStream(out);

        byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());

        response200Header(dos, body.length);
        responseBody(dos, body);
    }

    private void getAllUsers(BufferedReader br, OutputStream out) throws IOException {
        Map<String, String> headers = readHeader(br);
        Map<String, String> cookies = HttpRequestUtils.parseCookies(headers.get("Cookie"));

        if (cookies.get("logined") == null || !Boolean.parseBoolean(cookies.get("logined"))) {
            DataOutputStream dos = new DataOutputStream(out);
            response302Header(dos, "user/login.html");
            return;
        }

        Collection<User> all = DataBase.findAll();
        StringBuilder sb = new StringBuilder();

        sb.append("<html>");
        sb.append("<head><title>User List</title></head>");
        sb.append("<body>");
        sb.append("<h1>User List</h1>");
        sb.append("<table border=\"1\">");
        sb.append("<tr><th>ID</th><th>Name</th><th>Email</th></tr>");

        for (User user : all) {
            sb.append("<tr>");
            sb.append("<td>").append(user.getUserId()).append("</td>");
            sb.append("<td>").append(user.getName()).append("</td>");
            sb.append("<td>").append(user.getEmail()).append("</td>");
            sb.append("</tr>");
        }

        sb.append("</table>");
        sb.append("</body>");
        sb.append("</html>");

        String html = sb.toString();
        DataOutputStream dos = new DataOutputStream(out);
        response200Header(dos, html.length());
        responseBody(dos, html.getBytes());
    }

    private void login(final BufferedReader br, final OutputStream out) throws IOException {
        Map<String, String> headers = readHeader(br);
        Map<String, String> params = readParams(br, headers);

        boolean authenticated = DataBase.isValidUser(params.get("userId"), params.get("password"));
        log.debug("authenticated : {}", authenticated);

        DataOutputStream dos = new DataOutputStream(out);
        responseLoginHeader(dos, authenticated);

        File file = null;
        if (authenticated) {
            file = new File("./webapp" + "/index.html");
        } else {
            file = new File("./webapp" + "/user/login_failed.html");
        }

        responseBody(dos, Files.readAllBytes(file.toPath()));
    }

    private void responseLoginHeader(final DataOutputStream dos, final boolean authenticated) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Set-Cookie: logined=" + authenticated + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void createUser(final BufferedReader br, final OutputStream out) throws IOException {
        Map<String, String> headers = readHeader(br);
        Map<String, String> params = readParams(br, headers);

        User user = User.from(params);
        DataBase.addUser(user);

        log.debug("User : {}", user);

        DataOutputStream dos = new DataOutputStream(out);
        response302Header(dos, "index.html");
    }

    private Map<String, String> readParams(final BufferedReader br,
        final Map<String, String> headers) throws IOException {
        int contentLength = Integer.parseInt(headers.get("Content-Length"));
        String contentBody = IOUtils.readData(br, contentLength);
        return HttpRequestUtils.parseQueryString(contentBody);
    }

    private Map<String, String> readHeader(BufferedReader br) throws IOException {
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
        return headers;
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

    private void response302Header(DataOutputStream dos, String location) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: http://localhost:8080/" + location + "\r\n");
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
