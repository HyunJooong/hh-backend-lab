package com.choo.hhbackendlab.usecase.order;

import com.choo.hhbackendlab.entity.*;
import com.choo.hhbackendlab.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class GetOrderUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private GetOrderUseCase getOrderUseCase;

    @Test
    @DisplayName("주문 조회 성공 - 주문번호로 주문과 주문 상품 목록을 조회한다")
    void getOrder_Success() throws Exception {
        // given
        String orderNumber = "ORD-12345678";

        User user = createUser(1L, "testuser");
        Order order = createOrder(1L, orderNumber, user);

        Category category = createCategory(1L, "IT", "IT");
        Product product1 = createProduct(1L, "laptop", "노트북입니다.", category, 100000, 10);
        Product product2 = createProduct(2L, "mouse", "마우스입니다.", category, 30000, 20);

        // OrderItem 생성 시 order를 null로 하고, addOrderItem으로 추가 (총 금액 계산 포함)
        OrderItem orderItem1 = createOrderItem(1L, null, product1, 2, 100000);
        OrderItem orderItem2 = createOrderItem(2L, null, product2, 1, 30000);

        order.addOrderItem(orderItem1);
        order.addOrderItem(orderItem2);

        given(orderRepository.findByOrderNumberWithItems(orderNumber)).willReturn(Optional.of(order));

        // when
        Order result = getOrderUseCase.getOrder(orderNumber);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getOrderNumber()).isEqualTo(orderNumber);
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getOrderItems()).hasSize(2);

        // 첫 번째 주문 상품 검증
        OrderItem resultItem1 = result.getOrderItems().get(0);
        assertThat(resultItem1.getProduct().getName()).isEqualTo("laptop");
        assertThat(resultItem1.getQuantity()).isEqualTo(2);
        assertThat(resultItem1.getPrice()).isEqualTo(100000);
        assertThat(resultItem1.getTotalPrice()).isEqualTo(200000);

        // 두 번째 주문 상품 검증
        OrderItem resultItem2 = result.getOrderItems().get(1);
        assertThat(resultItem2.getProduct().getName()).isEqualTo("mouse");
        assertThat(resultItem2.getQuantity()).isEqualTo(1);
        assertThat(resultItem2.getPrice()).isEqualTo(30000);
        assertThat(resultItem2.getTotalPrice()).isEqualTo(30000);

        // 총 금액 검증
        assertThat(result.getTotalAmount()).isEqualTo(230000);
        assertThat(result.getFinalAmount()).isEqualTo(230000);

        verify(orderRepository).findByOrderNumberWithItems(orderNumber);
    }
    // Helper methods
    private User createUser(Long id, String username) throws Exception {
        User user = new User();
        setField(user, "id", id);
        setField(user, "username", username);
        return user;
    }

    private Order createOrder(Long id, String orderNumber, User user) throws Exception {
        Order order = new Order(orderNumber, user);
        setField(order, "id", id);
        return order;
    }

    private Category createCategory(Long id, String name, String code) throws Exception {
        Category category = new Category();
        setField(category, "id", id);
        setField(category, "name", name);
        setField(category, "code", code);
        return category;
    }

    private Product createProduct(Long id, String name, String description, Category category, int price, int stock) throws Exception {
        Product product = new Product(name, description, category, price, stock);
        setField(product, "id", id);
        return product;
    }

    private OrderItem createOrderItem(Long id, Order order, Product product, int quantity, int price) throws Exception {
        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .product(product)
                .quantity(quantity)
                .price(price)
                .build();
        setField(orderItem, "id", id);
        return orderItem;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
