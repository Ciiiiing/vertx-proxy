package com.example.vertxtest;

import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptInitializer;
import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptPipeline;
import com.github.monkeywie.proxyee.intercept.common.FullResponseIntercept;
import com.github.monkeywie.proxyee.server.HttpProxyServer;
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig;
import com.github.monkeywie.proxyee.util.HttpUtil;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProxyT {
  public static void main(String[] args) {
    HttpProxyServerConfig config = new HttpProxyServerConfig();
//    config.setHandleSsl(true);
    new HttpProxyServer()
      .serverConfig(config)
      .proxyInterceptInitializer(new HttpProxyInterceptInitializer() {
        @Override
        public void init(HttpProxyInterceptPipeline pipeline) {
          pipeline.addLast(new FullResponseIntercept() {
            @Override
            public boolean match(HttpRequest httpRequest, HttpResponse httpResponse, HttpProxyInterceptPipeline pipeline) {
              return HttpUtil.checkUrl(pipeline.getHttpRequest(), "^api.bilibili.com");
            }

            @Override
            public void handleResponse(HttpRequest httpRequest, FullHttpResponse httpResponse, HttpProxyInterceptPipeline pipeline) {
              log.info("Receive: {}", httpResponse.toString());
            }
          });
        }
      })
      .start(8888);
  }
}
