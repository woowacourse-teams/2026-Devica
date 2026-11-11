package com.wrb.devica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DevicaApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(DevicaApplication.class, args);
        if (context.getEnvironment().getProperty("coupang.price-refresh.run-once", Boolean.class, false)) {
            System.exit(SpringApplication.exit(context));
        }
    }

}
