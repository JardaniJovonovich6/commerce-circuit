package com.aks.ecommerce.api_gateway.filters;

import com.aks.ecommerce.api_gateway.filters.service.JwtService;
import jdk.dynalink.linker.LinkerServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class RequiredRoleGatewayFilterFactory extends AbstractGatewayFilterFactory<RequiredRoleGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(RequiredRoleGatewayFilterFactory.class);
    private final JwtService jwtService;
    private final FilteringWebHandler filteringWebHandler;

    RequiredRoleGatewayFilterFactory(JwtService jwtService, FilteringWebHandler filteringWebHandler){
        super(Config.class);
        this.jwtService = jwtService;
        this.filteringWebHandler = filteringWebHandler;
    }



    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {

            String AuthorizationHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

            log.info("This is the header : " + AuthorizationHeader);

            List<String> roles = jwtService.getUserRoleFromToken(AuthorizationHeader.substring(7));

            log.info("roles for the token given at request :  " + roles);

            if (!roles.contains(config.requiredRole.toLowerCase(Locale.ROOT))){

                log.info("--------Not Authorized for Admin---------");

                exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(403));
                return chain.filter(exchange);
            }
            log.info("-------------------Admin Authorization is confirmed-------------------------");
            return chain.filter(exchange);


        });

    }

    public static class Config{

        private String requiredRole ;

        public String getRequiredRole(){
            return requiredRole;
        }

        public void setRequiredRole(String requiredRole){
            this.requiredRole=requiredRole;
        }


    }

}
