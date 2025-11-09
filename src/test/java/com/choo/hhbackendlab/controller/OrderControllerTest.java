package com.choo.hhbackendlab.controller;

import com.choo.hhbackendlab.entity.Category;
import com.choo.hhbackendlab.entity.Order;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.entity.User;
import com.choo.hhbackendlab.usecase.order.CancelOrderUseCase;
import com.choo.hhbackendlab.usecase.order.CreateOrderUseCase;
import com.choo.hhbackendlab.usecase.order.GetOrderUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;

    @MockitoBean
    private GetOrderUseCase getOrderUseCase;

    @MockitoBean
    private CancelOrderUseCase cancelOrderUseCase;

    @Test
    @DisplayName("주문 생성 성공")
    void createOrder_Success() throws Exception {
        // given
        String requestBody = """
                {
                    "userId": 1,
                    "orderItems": [
                        {
                            "productId": 1,
                            "quantity": 2
                        },
                        {
                            "productId": 2,
                            "quantity": 1
                        }
                    ],
                    "amount": 130000,
                    "couponAmount": 10000
                }
                """;

        User user = createUser(1L, "testuser");
        Category category = createCategory(1L, "IT");
        Product product1 = createProduct(1L, "laptop", "노트북", category, 100000, 10);
        Product product2 = createProduct(2L, "mouse", "마우스", category, 30000, 20);

        Order order = new Order("ORD-20250101-0001", user);
        setField(order, "totalAmount", 130000);
        setField(order, "discountAmount", 10000);
        setField(order, "finalAmount", 120000);

        given(createOrderUseCase.createOrder(any())).willReturn(order);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("ORD-20250101-0001"))
                .andExpect(jsonPath("$.totalAmount").value(130000))
                .andExpect(jsonPath("$.discountAmount").value(10000))
                .andExpect(jsonPath("$.finalAmount").value(120000));
    }

    @Test
    @DisplayName("주문 조회 성공")
    void getOrder_Success() throws Exception {
        // given
        String orderNumber = "ORD-20250101-0001";

        User user = createUser(1L, "testuser");
        Order order = new Order(orderNumber, user);
        setField(order, "totalAmount", 100000);
        setField(order, "finalAmount", 100000);

        given(getOrderUseCase.getOrder(orderNumber)).willReturn(order);

        // when & then
        mockMvc.perform(get("/api/orders/{orderNumber}", orderNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.totalAmount").value(100000))
                .andExpect(jsonPath("$.finalAmount").value(100000));
    }

    @Test
    @DisplayName("주문 조회 실패 - 존재하지 않는 주문번호")
    void getOrder_OrderNotFound() throws Exception {
        // given
        String invalidOrderNumber = "ORD-99999999-9999";

        given(getOrderUseCase.getOrder(invalidOrderNumber))
                .willThrow(new IllegalArgumentException("주문을 찾을 수 없습니다."));

        // when & then
        mockMvc.perform(get("/api/orders/{orderNumber}", invalidOrderNumber))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("주문 취소 성공")
    void cancelOrder_Success() throws Exception {
        // given
        String orderNumber = "ORD-20250101-0001";

        doNothing().when(cancelOrderUseCase).cancelOrder(orderNumber);

        // when & then
        mockMvc.perform(patch("/api/orders/{orderNumber}/cancel", orderNumber))
                .andExpect(status().isOk())
                .andExpect(content().string("주문이 취소되었습니다. 주문번호: " + orderNumber));
    }

    @Test
    @DisplayName("주문 취소 실패 - 존재하지 않는 주문")
    void cancelOrder_OrderNotFound() throws Exception {
        // given
        String invalidOrderNumber = "ORD-99999999-9999";

        doThrow(new IllegalArgumentException("주문을 찾을 수 없습니다."))
                .when(cancelOrderUseCase).cancelOrder(invalidOrderNumber);

        // when & then
        mockMvc.perform(patch("/api/orders/{orderNumber}/cancel", invalidOrderNumber))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("주문 취소 실패 - 이미 취소된 주문")
    void cancelOrder_AlreadyCancelled() throws Exception {
        // given
        String orderNumber = "ORD-20250101-0001";

        doThrow(new IllegalStateException("이미 취소된 주문입니다."))
                .when(cancelOrderUseCase).cancelOrder(orderNumber);

        // when & then
        mockMvc.perform(patch("/api/orders/{orderNumber}/cancel", orderNumber))
                .andExpect(status().is4xxClientError());
    }

    // Helper methods
    private User createUser(Long id, String username) throws Exception {
        User user = new User();
        setField(user, "id", id);
        setField(user, "username", username);
        return user;
    }

    private Category createCategory(Long id, String name) throws Exception {
        Category category = new Category();
        setField(category, "id", id);
        setField(category, "name", name);
        return category;
    }

    private Product createProduct(Long id, String name, String description, Category category, int price, int stock) throws Exception {
        Product product = new Product(name, description, category, price, stock);
        setField(product, "id", id);
        return product;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}