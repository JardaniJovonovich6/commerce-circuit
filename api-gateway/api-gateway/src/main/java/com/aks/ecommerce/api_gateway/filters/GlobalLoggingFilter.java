package com.aks.ecommerce.api_gateway.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class GlobalLoggingFilter implements GlobalFilter , Ordered {

    private static final Logger log = LoggerFactory.getLogger(GlobalLoggingFilter.class);
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        log.info("----------------- This is the Pre-Global starting pipeline -------------");
        log.info("This is Global Filter chain with Request URL : {}" , exchange.getRequest().getURI());
        log.info("This is Global Filter chain with Request SSL info  : {}" , exchange.getRequest().getSslInfo());
        log.info("This is Global Filter chain with Request Session  : {}" , exchange.getSession());
        log.info("This is Global Filter chain with Request Body  : {}" , exchange.getRequest().getBody());
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            log.info("------------------ This is Post order of the Chain , Request is completed chain is also Done --------------------");
            log.info("This is Global filter chain with status code for the reponse as : {}" , exchange.getResponse().getStatusCode());
        }));

    }

    @Override
    public int getOrder() {
        return 1;
    }
}
