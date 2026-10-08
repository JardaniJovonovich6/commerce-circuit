package com.aks.ecommerce.order_service.config;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@RefreshScope
public class DevPanelConfiguration {

    @Value("${devPanel.toggle}")
    private Boolean devPaneltoggle;

}
