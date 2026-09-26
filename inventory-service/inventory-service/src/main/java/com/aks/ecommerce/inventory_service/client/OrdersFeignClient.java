package com.aks.ecommerce.inventory_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;


@FeignClient(name = "order-service" , path = "/api/v1")
public interface OrdersFeignClient {

    @GetMapping("/orders/helloOrders")
    String getOrdersUsingFeign();

}
