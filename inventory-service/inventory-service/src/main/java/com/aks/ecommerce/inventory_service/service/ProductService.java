package com.aks.ecommerce.inventory_service.service;

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




}
