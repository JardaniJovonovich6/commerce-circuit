package com.aks.ecommerce.order_service.controller;

import com.aks.ecommerce.order_service.dto.OrderRequestDto;
import com.aks.ecommerce.order_service.service.OrdersService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;


import java.util.List;


@RequestMapping("/orders")
@Slf4j
@RestController
@RequiredArgsConstructor
public class OrdersController {

    private final OrdersService orderService;
    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;

    @GetMapping("/helloOrders")
    public String helloOrders(@RequestHeader("X-User-Id") Long userId) {
        return "Hello from Orders Service";
    }

    @PostMapping("/create")
    public ResponseEntity<OrderRequestDto> createOrders(@RequestBody OrderRequestDto orderRequestDto){

        OrderRequestDto OrderRequestDtoSaved = orderService.createOrders(orderRequestDto);
        return ResponseEntity.ok(OrderRequestDtoSaved);

    }

    @DeleteMapping("cancel/{id}")
    public ResponseEntity<String> cancel(@PathVariable Long id){
        boolean responseFromService = orderService.cancelOrder(id);

        if(responseFromService == false)
            return ResponseEntity.badRequest().body("Order was already delivered with order id " + id);
        else
            return ResponseEntity.accepted().body("Order is cancelled for the ID : " + id);
    }

    @GetMapping
    public ResponseEntity<List<OrderRequestDto>> getAllOrders(HttpServletRequest httpServletRequest) {
        log.info("Fetching all orders via controller");
        List<OrderRequestDto> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);  // Returns 200 OK with the list of orders
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderRequestDto> getOrderById(@PathVariable Long id) {
        log.info("Fetching order with ID: {} via controller", id);
        OrderRequestDto order = orderService.getOrderById(id);
        return ResponseEntity.ok(order);  // Returns 200 OK with the order
    }

    @GetMapping("/hotsales")
    public String getInventoryListFromOrdersModule(){
        ServiceInstance inventoryInstance = discoveryClient.getInstances("inventory-service").getFirst();
        log.info(String.valueOf(inventoryInstance));

        return restClient.get()
                .uri(inventoryInstance.getUri() + "/api/v1/products/getproducts")
                .retrieve()
                .body(String.class);
    }
}
