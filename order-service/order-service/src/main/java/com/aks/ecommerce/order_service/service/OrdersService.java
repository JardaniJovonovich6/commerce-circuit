package com.aks.ecommerce.order_service.service;

import com.aks.ecommerce.order_service.clients.InventoryOpenFeignClient;
import com.aks.ecommerce.order_service.dto.OrderRequestDto;
import com.aks.ecommerce.order_service.entity.OrderItem;
import com.aks.ecommerce.order_service.entity.OrderStatus;
import com.aks.ecommerce.order_service.entity.Orders;
import com.aks.ecommerce.order_service.repoitory.OrdersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public OrderRequestDto createOrders(OrderRequestDto orderRequestDto) {
        Double totalPrice = inventoryOpenFeignClient.reduceStocks(orderRequestDto);


        Orders orders = modelMapper.map(orderRequestDto , Orders.class);
        for(OrderItem orderItems: orders.getItems()){
            orderItems.setOrder(orders);
        }
        orders.setTotalPrice(totalPrice);
        orders.setOrderStatus(OrderStatus.CONFIRMED);

        Orders savedOrder = orderRepository.save(orders);

        return modelMapper.map(savedOrder , OrderRequestDto.class);
    }
}
