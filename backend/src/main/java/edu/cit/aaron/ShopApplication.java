package edu.cit.aaron;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Parent package on purpose: component scanning starts here and covers both
 * edu.cit.aaron.shop (Order module) and edu.cit.aaron.inventory (Inventory module).
 */
@SpringBootApplication
public class ShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
