package com.aks.ecommerce.inventory_service.controller;

import com.aks.ecommerce.inventory_service.client.OrdersFeignClient;
import com.aks.ecommerce.inventory_service.dto.OrderRequestDto;
import com.aks.ecommerce.inventory_service.dto.Productdto;
import com.aks.ecommerce.inventory_service.entity.Product;
import com.aks.ecommerce.inventory_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    @Value("${my.globalVariable}")
    private String myGLobalVariable;

    private final ProductService productService;
    private final ModelMapper modelMapper;
    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;
    private final OrdersFeignClient ordersFeignClient;


    @GetMapping("/fetchOrders")
    public String fetchOrdersFromOrderService(){
        ServiceInstance serviceInstance  = discoveryClient.getInstances("order-service").getFirst();
        List<ServiceInstance> serviceInstanceList = discoveryClient.getInstances("order-service");
        log.info(serviceInstance.toString());
        log.info(serviceInstanceList.toString());

        return restClient.get()
                .uri(serviceInstance.getUri()+"/api/v1/orders/helloOrders")
                .retrieve()
                .body(String.class);

    }

    @GetMapping("/adminPanel")
    public String adminpanel(){
        return "Welsome to Inventory Admin Panel : " + myGLobalVariable;
    }

    @GetMapping("/fetchOrdersUsingFeignClient")
    public String fetchOrdersUsingFeignClientFromOrderSevice(){
        return ordersFeignClient.getOrdersUsingFeign();
    }



    @PostMapping("/create")
    public ResponseEntity<Productdto> createProduct(@RequestBody Productdto productdto){
        productService.createProducts(modelMapper.map(productdto , Product.class));
        return new ResponseEntity<>(productdto , HttpStatusCode.valueOf(200));
    }

    @GetMapping("/getproducts")
    public ResponseEntity<List<Productdto>> getallproducts(){
        return ResponseEntity.ok(productService.getAllProducts().stream()
                .map(item -> modelMapper.map(item , Productdto.class))
                .toList()
        );
    }

    @PutMapping("/reduce-stocks")
    public ResponseEntity<Double> reduceStocks(@RequestBody OrderRequestDto orderRequestDto){
        Double TotalPrice = productService.reduceStocks(orderRequestDto);
        return ResponseEntity.ok(TotalPrice);
    }

    @PutMapping("/addStocks")
    public ResponseEntity<String> addStocks(@RequestBody OrderRequestDto orderRequestDto){
        productService.addStocks(orderRequestDto);
        return ResponseEntity.accepted().body("The Stocks where updated ....");
    }



}
