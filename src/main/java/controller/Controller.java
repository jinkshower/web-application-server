package controller;

import http.HttpRequest;
import http.HttpResponse;
import java.util.HashMap;

public interface Controller {
    void service(HttpRequest request, HttpResponse response);
}
