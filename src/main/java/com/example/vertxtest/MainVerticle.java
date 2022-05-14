package com.example.vertxtest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.*;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.cert.Certificate;
import java.util.logging.LogManager;

@Slf4j
public class MainVerticle extends AbstractVerticle {
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  static class Address {
    String host;
    Integer port;
    NetSocket proxySocket;
    boolean first = true;
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    proxyTest();
  }

  /**
   * 实验 netClient 发送 http 请求
   */
  public void sendTest() {
    NetClientOptions netClientOptions = new NetClientOptions().setLogActivity(true);
    NetClient netClient = vertx.createNetClient(netClientOptions);

    WebClientOptions webClientOptions = new WebClientOptions().setLogActivity(true);
    WebClient webClient = WebClient.create(vertx, webClientOptions);

//    webClient.get( 443,"vertx.io", "")
//      .ssl(true)
//      .putHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/101.0.4951.54 Safari/537.36 Edg/101.0.1210.39")
//      .send()
//      .onSuccess(resp -> {
//        log.info("web client receive: {}", resp.body());
//      });
//
    netClient.connect(443, "vertx.io")
      .onSuccess(hand -> {
        log.info("conn success");
//        hand.upgradeToSsl().result();
        hand.handler(buffer -> {
          log.info("client receive: {}", buffer);
        });
        hand.write("GET / HTTP/1.1\r\nuser-agent: Vert.x-WebClient/4.2.7\r\nhost: vertx.io\r\n\r\n", "utf-8");
      })
      .onFailure(hand -> {
        log.info("fail");
        hand.printStackTrace();
      });
  }

  /**
   * 支持 http, https 代理
   */
  public void proxyTest() {
    NetServerOptions netServerOptions = new NetServerOptions().setLogActivity(false);
    NetServer netServer = vertx.createNetServer(netServerOptions);

    NetClientOptions netClientOptions = new NetClientOptions().setLogActivity(false);
    NetClient netClient = vertx.createNetClient(netClientOptions);

    netServer.connectHandler(clientSocket -> {
        Address address = new Address();
        clientSocket.handler(buffer -> {
          log.info("net server receive: {}", buffer);
          if (address.first) {
            address.first = false;
            String[] headers = buffer.toString().split("\n");
            String host = headers[1].replace("Host: ", "");
            address.host = host.replace("\r", "");
            address.port = 80;
          }
          // CONNECT 是 https 代理模式的第一个请求报文，给客户端返回 200 即可
          if (buffer.toString().startsWith("CONNECT")) {
            address.port = 443;
            address.host = address.host.split(":")[0];
            clientSocket.write("HTTP/1.1 200 Connection Established\r\n\r\n");
            return;
          }
          if (address.proxySocket == null) {
            log.info("proxy is null, start to connect to {}:{}", address.host, address.port);
            netClient.connect(address.port, address.host)
              .onSuccess(handler -> {
                log.info("success connect to {}:{}", address.host, address.port);
                address.proxySocket = handler;
                address.proxySocket.handler(reBuff -> {
                  log.info("proxy client receive: {}", reBuff);
                  clientSocket.write(reBuff);
                });
                address.proxySocket.write(buffer);
              })
              .onFailure(Throwable::printStackTrace);
          }
          if (address.proxySocket != null) {
            address.proxySocket.handler(reBuff -> {
              log.info("proxy client receive: {}", reBuff);
              clientSocket.write(reBuff);
            });
            address.proxySocket.write(buffer);
          }
        });
      })
      .listen(8888)
      .onSuccess(handler -> {
        log.info("net server start");
      })
      .onFailure(handler -> {
        log.info("net server start fail: {}", handler.getMessage());
      });
  }

  /**
   * 对 net/http server/client 的实验
   */
  public void servTest() {
    NetServerOptions netServerOptions = new NetServerOptions().setLogActivity(true);
    NetServer netServer = vertx.createNetServer(netServerOptions);

    NetClientOptions netClientOptions = new NetClientOptions().setLogActivity(true);
    NetClient netClient = vertx.createNetClient(netClientOptions);

    HttpServerOptions httpServerOptions = new HttpServerOptions().setLogActivity(true);
    HttpServer httpServer = vertx.createHttpServer(httpServerOptions);

    WebClientOptions webClientOptions = new WebClientOptions().setLogActivity(true);
    WebClient webClient = WebClient.create(vertx, webClientOptions);

    netServer.connectHandler(handler -> {
      handler.handler(buffer -> {
        log.info("net server receive: {}", buffer);
      });
      handler.write("HTTP/1.1 200 OK\rcontent-length: 11\rHi, Welcome");
    });

    netServer.listen(8888, handler -> {
      if (handler.succeeded()) {
        log.info("net server started");
      } else {
        log.info("net server start failed: {}", handler.cause().getMessage());
      }
    });

    httpServer.requestHandler(handler -> {
      handler.handler(buffer -> {
        log.info("http server receive: {}", buffer);
      });
      handler.response().end("Hi, Welcome");
    });

    httpServer.listen(9999)
      .onSuccess(handler -> log.info("http server started on port 9999"))
      .onFailure(handler -> log.info("http server start fail: {}", handler.getMessage()));

    webClient.get(9999, "localhost", "/test").send()
      .onSuccess(resp -> {
        log.info("web client receive: {}", resp.body());
      })
      .onFailure(resp -> {
        log.info("web client fail: {}", resp.getMessage());
      });
  }
}
