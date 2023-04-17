package com.rajeev.orderservice.service;

import com.rajeev.orderservice.dto.InventoryResponse;
import com.rajeev.orderservice.dto.OrderLineItemsDto;
import com.rajeev.orderservice.dto.OrderRequest;
import com.rajeev.orderservice.event.OrderPlacedEvent;
import com.rajeev.orderservice.model.Order;
import com.rajeev.orderservice.model.OrderLineItems;
import com.rajeev.orderservice.repository.OrderRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObservationRegistry observationRegistry;
    private final ApplicationEventPublisher applicationEventPublisher;

    public String placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        //Get Order line items from order request
        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        //Set Order line items List from order request
        order.setOrderLineItemsList(orderLineItems);

        //get list of Sku Code from each order line item list
        List<String> skuCodeList = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        // Call Inventory Service, and place order if product is in stock
        Observation inventoryServiceObservation = Observation.createNotStarted("inventory-service-lookup",
                this.observationRegistry)
                .contextualName("Checking-SkuCode-Inventory")
                .lowCardinalityKeyValue("call", "inventory-service");

        return inventoryServiceObservation.observe(() ->{
            InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                    .uri("http://inventory-service/api/inventory",
                            uriBuilder -> uriBuilder.queryParam("skuCode", skuCodeList).build())
                    .retrieve()
                    .bodyToMono(InventoryResponse[].class)
                    .block();

            boolean allProductsInStock = Arrays.stream(inventoryResponseArray).allMatch(InventoryResponse::isInStock);

            if (allProductsInStock) {
                orderRepository.save(order);
                applicationEventPublisher.publishEvent(new OrderPlacedEvent((order.getOrderNumber())));
                return "Order Placed Successfully";
            } else {
                throw new IllegalArgumentException("Product is not in stock, please try again later");
            }
        });
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
