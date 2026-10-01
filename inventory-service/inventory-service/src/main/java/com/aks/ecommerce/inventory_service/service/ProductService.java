package com.aks.ecommerce.inventory_service.service;

import com.aks.ecommerce.inventory_service.dto.OrderRequestDto;
import com.aks.ecommerce.inventory_service.dto.OrderRequestItemDto;
import com.aks.ecommerce.inventory_service.entity.Product;
import com.aks.ecommerce.inventory_service.repository.ProductRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    public Product createProducts(Product product){
        productRepository.save(product);
        return product;
    }

    public List<Product> getAllProducts(){
        return productRepository.findAll();
    }


    public Double reduceStocks(OrderRequestDto orderRequestDto){
        Double totalPrice = 0.0;
        for(OrderRequestItemDto orderRequestItemDto : orderRequestDto.getItems()){
            Long productRequestId = orderRequestItemDto.getProductId();
            Product product = productRepository.findById(productRequestId)
                    .orElseThrow(() -> new RuntimeException("Product with id " + productRequestId + " was not Found ......."));

            if(product.getStock() < orderRequestItemDto.getQuantity()){
                throw new RuntimeException("Stocks on this item is low : " + productRequestId);
            }
            product.setStock(product.getStock()-orderRequestItemDto.getQuantity());
            productRepository.save(product);

            Double itemprice = product.getPrice()*orderRequestItemDto.getQuantity();
            totalPrice += itemprice;
        }

        return totalPrice;
    }

    public void addStocks(OrderRequestDto orderRequestDto){
        for(OrderRequestItemDto item : orderRequestDto.getItems()){
            Long productRequestId = item.getProductId();
            Integer productQuantity = item.getQuantity();

            Product product = productRepository.findById(productRequestId)
                    .orElseThrow(() -> new RuntimeException("Product is not present in Inventory with ID : " + productRequestId + " For Cancellation purpose"));

            product.setStock(product.getStock() + productQuantity);
            productRepository.save(product);
        }
    }






}
