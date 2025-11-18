package com.choo.hhbackendlab.integration;

import com.choo.hhbackendlab.dto.requestDto.OrderItemRequest;
import com.choo.hhbackendlab.dto.requestDto.OrderRequest;
import com.choo.hhbackendlab.entity.*;
import com.choo.hhbackendlab.repository.*;
import com.choo.hhbackendlab.usecase.order.CreateOrderUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CreateOrderUseCase 통합 테스트
 * TestContainer를 사용하여 실제 MySQL 환경에서 주문 생성 기능을 검증
 */
@SpringBootTest
@Testcontainers
public class OrderTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.40")
            .withDatabaseName("hh_backend_lab")
            .withUsername("root")
            .withPassword("1234");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PointWalletRepository pointWalletRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private User testUser;
    private Product testProduct1;
    private Product testProduct2;
    private Category testCategory;

    @BeforeEach
    void setUp() throws Exception {
        // 기존 데이터 정리
        orderRepository.deleteAll();
        couponRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        pointWalletRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트 카테고리 생성
        testCategory = new Category();
        setField(testCategory, "name", "전자제품");
        setField(testCategory, "code", "ELEC");
        testCategory = categoryRepository.save(testCategory);

        // 테스트 사용자 생성
        testUser = new User();
        testUser = userRepository.save(testUser);

        // PointWallet 생성 (충분한 잔액)
        PointWallet pointWallet = new PointWallet(testUser, 100000);
        pointWalletRepository.save(pointWallet);

        // 테스트 상품 생성
        testProduct1 = new Product("노트북", "고성능 노트북", testCategory, 50000, 10);
        testProduct1 = productRepository.save(testProduct1);

        testProduct2 = new Product("마우스", "무선 마우스", testCategory, 20000, 20);
        testProduct2 = productRepository.save(testProduct2);
    }

    @Test
    @Transactional
    @DisplayName("주문 생성 성공 - 쿠폰 없이 단일 상품 주문")
    void createOrder_Success_WithoutCoupon_SingleProduct() {
        // given
        OrderItemRequest orderItemRequest = createOrderItemRequest(testProduct1.getId(), 2);
        OrderRequest orderRequest = createOrderRequest(
                testUser.getId(),
                List.of(orderItemRequest),
                100000,
                null
        );

        // when
        Order order = createOrderUseCase.createOrder(orderRequest);

        // then
        assertThat(order).isNotNull();
        assertThat(order.getId()).isNotNull();
        assertThat(order.getOrderNumber()).isNotNull();
        assertThat(order.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(order.getOrderItems()).hasSize(1);
        assertThat(order.getTotalAmount()).isEqualTo(100000);
        assertThat(order.getDiscountAmount()).isEqualTo(0);
        assertThat(order.getFinalAmount()).isEqualTo(100000);
        assertThat(order.isCancelled()).isFalse();

        // 재고 차감 확인
        Product updatedProduct = productRepository.findById(testProduct1.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(8);

        // 포인트 차감 확인
        PointWallet updatedWallet = pointWalletRepository.findByUserIdWithLock(testUser.getId()).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualTo(0);
    }

    private OrderRequest createOrderRequest(Long userId, List<OrderItemRequest> orderItems, int amount, Long couponId) {
        try {
            OrderRequest request = new OrderRequest();
            setField(request, "userId", userId);
            setField(request, "orderItems", orderItems);
            setField(request, "amount", amount);
            setField(request, "couponId", couponId);
            return request;
        } catch (Exception e) {
            throw new RuntimeException("OrderRequest 생성 실패", e);
        }
    }

    private OrderItemRequest createOrderItemRequest(Long productId, Integer quantity) {
        try {
            OrderItemRequest request = new OrderItemRequest();
            setField(request, "productId", productId);
            setField(request, "quantity", quantity);
            return request;
        } catch (Exception e) {
            throw new RuntimeException("OrderItemRequest 생성 실패", e);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
