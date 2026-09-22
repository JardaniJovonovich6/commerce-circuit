package com.aks.ecommerce.inventory_service.dto;

import lombok.Data;

@Data
public class Productdto {


    private Long id;

    private String name;

    private Double price;

    private Integer stock;
}
