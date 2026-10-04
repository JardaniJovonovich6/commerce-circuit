package com.aks.ecommerce.api_gateway.filters;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OrdersLoggingFilter extends AbstractGatewayFilterFactory<OrdersLoggingFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(OrdersLoggingFilter.class);

    public OrdersLoggingFilter(){
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {

        return (exchange , chain) -> {
          log.info("Order filter pre : {} " , exchange.getRequest().getURI());
          return chain.filter(exchange);
        };

    }

    public static class Config {

    }

}
