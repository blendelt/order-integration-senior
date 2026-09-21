package com.example.orders.client;

import com.example.orders.client.dto.ErpOrderResponse;
import com.example.orders.entity.Order;

public interface ErpClient {

    ErpOrderResponse send(Order order);
}
