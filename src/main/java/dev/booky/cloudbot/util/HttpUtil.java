package dev.booky.cloudbot.util;
// Created by booky10 in BetterMcDecompiler (16:27 15.09.22)

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class HttpUtil {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static <T> T getJoin(URI uri, HttpResponse.BodyHandler<T> handler) {
        return get(uri, handler).join();
    }

    public static <T> CompletableFuture<T> get(URI uri, HttpResponse.BodyHandler<T> handler) {
        return HTTP.sendAsync(HttpRequest.newBuilder(uri).build(), handler).thenApply(resp -> {
            assert isSuccess(resp.statusCode()) : "Invalid http response code: " + resp;
            return resp.body();
        });
    }

    private static boolean isSuccess(int httpCode) {
        return httpCode >= 200 && httpCode < 300;
    }
}
