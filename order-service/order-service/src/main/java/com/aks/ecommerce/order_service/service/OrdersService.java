package com.aks.ecommerce.order_service.service;

import com.aks.ecommerce.order_service.clients.InventoryOpenFeignClient;
import com.aks.ecommerce.order_service.dto.OrderRequestDto;
import com.aks.ecommerce.order_service.entity.OrderItem;
import com.aks.ecommerce.order_service.entity.OrderStatus;
import com.aks.ecommerce.order_service.entity.Orders;
import com.aks.ecommerce.order_service.repoitory.OrdersRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrdersService {

    private final OrdersRepository orderRepository;
    private final ModelMapper modelMapper;
    private final InventoryOpenFeignClient inventoryOpenFeignClient;

    public List<OrderRequestDto> getAllOrders() {
        log.info("Fetching all orders");
        List<Orders> orders = orderRepository.findAll();
        return orders.stream().map(order -> modelMapper.map(order, OrderRequestDto.class)).toList();
    }

    public OrderRequestDto getOrderById(Long id) {
        log.info("Fetching order with ID: {}", id);
        Orders order = orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
        return modelMapper.map(order, OrderRequestDto.class);
    }


//    @CircuitBreaker(name = "inventoryFeignClient" , fallbackMethod = "createOrdersFallback")
//    @Retry(name = "inventoryRetry" , fallbackMethod = "createOrdersFallback")
//    @RateLimiter(name = "inventoryRateLimiter" , fallbackMethod = "createOrdersFallback")
    public OrderRequestDto createOrders(OrderRequestDto orderRequestDto) {
        log.info("Calling reduceStocks by OpenFeignCleint");
        Double totalPrice = inventoryOpenFeignClient.reduceStocks(orderRequestDto);
        log.info("Calling createOrder method");


        Orders orders = modelMapper.map(orderRequestDto , Orders.class);
        for(OrderItem orderItems: orders.getItems()){
            orderItems.setOrder(orders);
        }
        orders.setTotalPrice(totalPrice);
        orders.setOrderStatus(OrderStatus.CONFIRMED);

        Orders savedOrder = orderRepository.save(orders);

        return modelMapper.map(savedOrder , OrderRequestDto.class);
    }

    public OrderRequestDto createOrdersFallback(OrderRequestDto orderRequestDto , Throwable throwable){
        log.error("Fallback occured due to an Error : {}", throwable.getMessage());
        return new OrderRequestDto();
    }

    public boolean cancelOrder(Long id) {
        Orders order = modelMapper.map(orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order was not found with ID : " + id)) , Orders.class);
        if(order.getOrderStatus() == OrderStatus.DELIVERED){
            return false;
        }
        order.setOrderStatus(OrderStatus.CANCELLED);
        log.info("Adding stocks ..... " + id);
        inventoryOpenFeignClient.addStocks(modelMapper.map(order , OrderRequestDto.class));

        log.info("Stocks added ........ " + id);

        orderRepository.save(order);

        return true;

    }

//    public OrderRequestDto createOrdersFallbackRateLimiter(OrderRequestDto orderRequestDto , Throwable throwable){
//        log.error("Fallback occured due to RateLimiter (This is a Custom Message by SLF4J , not from RateLimiter)");
//        return  new OrderRequestDto();
//    }
}
