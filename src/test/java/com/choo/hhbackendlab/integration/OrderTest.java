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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
    // Redis 컨테이너 추가
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Redis 동적 속성 설정
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
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

    @Autowired
    private UserCouponRepository userCouponRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        userCouponRepository.deleteAll();
        pointWalletRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("동시에 같은 상품을 주문할 때 상품별 분산락으로 overselling 방지")
    void createOrder_ConcurrentOrders_PreventOverselling() throws InterruptedException {
        // Given
        int threadCount = 10; // 동시 주문 요청 수
        int orderQuantityPerThread = 1; // 각 주문당 수량
        int initialStock = 5; // 초기 재고 (10개 요청 중 5개만 성공해야 함)

        // 1. 카테고리 생성
        Category category = createCategory("전자제품", "ELECTRONICS");
        categoryRepository.save(category);

        // 2. 상품 생성 (재고: 5개)
        Product product = new Product("노트북", "고성능 노트북", category, 500000, initialStock);
        productRepository.save(product);

        // 3. 사용자 10명 생성 및 포인트 지급
        User[] users = new User[threadCount];
        for (int i = 0; i < threadCount; i++) {
            User user = createUser("user" + i, "user" + i + "@test.com", "password123");
            userRepository.save(user);
            users[i] = user;

            // 각 사용자에게 충분한 포인트 지급
            PointWallet pointWallet = new PointWallet(user, 1000000);
            pointWalletRepository.save(pointWallet);
        }

        // 4. 동시성 테스트 준비
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 10명의 사용자가 동시에 같은 상품을 주문 (재고 5개, 요청 10개)
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    // 주문 요청 생성
                    OrderItemRequest orderItemRequest = new OrderItemRequest();
                    setField(orderItemRequest, "productId", product.getId());
                    setField(orderItemRequest, "quantity", orderQuantityPerThread);

                    OrderRequest orderRequest = new OrderRequest();
                    setField(orderRequest, "userId", users[index].getId());
                    setField(orderRequest, "orderItems", List.of(orderItemRequest));
                    setField(orderRequest, "amount", 500000);

                    // 주문 생성 시도
                    createOrderUseCase.createOrder(orderRequest);
                    successCount.incrementAndGet();

                } catch (IllegalStateException e) {
                    // 재고 부족으로 실패한 경우
                    System.out.println("재고 부족: " + e.getMessage());
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    // 기타 예외
                    System.out.println("예외 발생: " + e.getClass().getName() + " - " + e.getMessage());
                    e.printStackTrace();
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 완료될 때까지 대기
        latch.await();
        executorService.shutdown();

        // Then
        // 1. 성공한 주문 수 확인 (재고 5개이므로 5개만 성공해야 함)
        assertThat(successCount.get()).isEqualTo(initialStock);
        assertThat(failCount.get()).isEqualTo(threadCount - initialStock);

        // 2. 실제 생성된 주문 수 확인
        long orderCount = orderRepository.count();
        assertThat(orderCount).isEqualTo(initialStock);

        // 3. 최종 재고 확인 (0개가 되어야 함)
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(0);

        // 4. 총 주문 수량 확인 (초기 재고 5개와 일치해야 함)
        System.out.println("성공 횟수: " + successCount.get());
        System.out.println("실패 횟수: " + failCount.get());
        System.out.println("실제 주문 수: " + orderCount);
    }

    @Test
    @DisplayName("여러 상품을 동시에 주문할 때 상품별 분산락으로 데드락 방지")
    void createOrder_MultipleProducts_PreventDeadlock() throws InterruptedException {
        // Given
        int threadCount = 5;

        // 1. 카테고리 생성
        Category category = createCategory("전자제품", "ELECTRONICS");
        categoryRepository.save(category);

        // 2. 상품 2개 생성
        Product product1 = new Product("노트북", "고성능 노트북", category, 500000, 10);
        productRepository.save(product1);

        Product product2 = new Product("마우스", "무선 마우스", category, 50000, 10);
        productRepository.save(product2);

        // 3. 사용자 5명 생성 및 포인트 지급
        User[] users = new User[threadCount];
        for (int i = 0; i < threadCount; i++) {
            User user = createUser("user" + i, "user" + i + "@test.com", "password123");
            userRepository.save(user);
            users[i] = user;

            PointWallet pointWallet = new PointWallet(user, 10000000);
            pointWalletRepository.save(pointWallet);
        }

        // 4. 동시성 테스트 준비
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 5명의 사용자가 동시에 2개 상품을 주문 (상품 ID 정렬로 데드락 방지)
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    // 주문 요청 생성 (상품 2개)
                    OrderItemRequest item1 = new OrderItemRequest();
                    setField(item1, "productId", product1.getId());
                    setField(item1, "quantity", 1);

                    OrderItemRequest item2 = new OrderItemRequest();
                    setField(item2, "productId", product2.getId());
                    setField(item2, "quantity", 1);

                    OrderRequest orderRequest = new OrderRequest();
                    setField(orderRequest, "userId", users[index].getId());
                    setField(orderRequest, "orderItems", List.of(item1, item2));
                    setField(orderRequest, "amount", 550000);

                    // 주문 생성 시도
                    createOrderUseCase.createOrder(orderRequest);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드가 완료될 때까지 대기
        latch.await();
        executorService.shutdown();

        // Then
        // 1. 모든 주문이 성공해야 함 (데드락 없이 완료)
        assertThat(successCount.get()).isEqualTo(threadCount);

        // 2. 실제 생성된 주문 수 확인
        long orderCount = orderRepository.count();
        assertThat(orderCount).isEqualTo(threadCount);

        // 3. 각 상품의 최종 재고 확인
        Product updatedProduct1 = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(updatedProduct1.getStock()).isEqualTo(10 - threadCount);

        Product updatedProduct2 = productRepository.findById(product2.getId()).orElseThrow();
        assertThat(updatedProduct2.getStock()).isEqualTo(10 - threadCount);
    }

    @Test
    @DisplayName("포인트와 쿠폰을 사용하여 주문 생성 성공")
    void createOrderWithPointAndCoupon_Success() {
        // Given
        // 1. 카테고리 생성
        Category category = createCategory("전자제품", "ELECTRONICS");
        categoryRepository.save(category);

        // 2. 상품 생성 (가격: 500,000원, 재고: 10개)
        Product product = new Product("노트북", "고성능 노트북", category, 500000, 10);
        productRepository.save(product);

        // 3. 사용자 생성
        User user = createUser("testuser", "test@test.com", "password123");
        userRepository.save(user);

        // 4. 포인트 지갑 생성 (초기 잔액: 1,000,000 포인트)
        PointWallet pointWallet = new PointWallet(user, 1000000);
        pointWalletRepository.save(pointWallet);

        // 5. 쿠폰 템플릿 생성 (할인 금액: 50,000원, 최소 주문 금액: 100,000원)
        Coupon coupon = new Coupon(
                "신규 회원 할인 쿠폰",
                100,
                50000,
                100000,
                LocalDateTime.now().plusDays(30)
        );
        couponRepository.save(coupon);

        // 6. 사용자 쿠폰 발급
        UserCoupon userCoupon = new UserCoupon(coupon, user);
        userCouponRepository.save(userCoupon);

        // 7. 주문 요청 생성 (수량: 2개)
        OrderItemRequest orderItemRequest = new OrderItemRequest();
        setField(orderItemRequest, "productId", product.getId());
        setField(orderItemRequest, "quantity", 2);

        OrderRequest orderRequest = new OrderRequest();
        setField(orderRequest, "userId", user.getId());
        setField(orderRequest, "orderItems", List.of(orderItemRequest));
        setField(orderRequest, "couponId", userCoupon.getId());
        setField(orderRequest, "amount", 950000); // 총 금액 1,000,000 - 쿠폰 할인 50,000 = 950,000

        // When
        Order savedOrder = createOrderUseCase.createOrder(orderRequest);

        // Then
        // 1. 주문이 생성되었는지 확인
        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getId()).isNotNull();

        // 2. 주문 정보 확인
        assertThat(savedOrder.getUser().getId()).isEqualTo(user.getId());
        assertThat(savedOrder.getOrderNumber()).isNotNull();
        assertThat(savedOrder.getOrderNumber()).startsWith("ORD-");
        assertThat(savedOrder.getOrderedAt()).isNotNull();
        assertThat(savedOrder.getCancelledAt()).isNull();

        // 3. 주문 아이템 확인
        assertThat(savedOrder.getOrderItems()).hasSize(1);
        OrderItem orderItem = savedOrder.getOrderItems().get(0);
        assertThat(orderItem.getProduct().getId()).isEqualTo(product.getId());
        assertThat(orderItem.getQuantity()).isEqualTo(2);
        assertThat(orderItem.getPrice()).isEqualTo(500000);
        assertThat(orderItem.getTotalPrice()).isEqualTo(1000000); // 500,000 * 2

        // 4. 가격 계산 확인
        // 총 주문 금액: 500,000 * 2 = 1,000,000
        // 쿠폰 할인: 50,000
        // 최종 결제 금액: 1,000,000 - 50,000 = 950,000
        assertThat(savedOrder.getTotalAmount()).isEqualTo(1000000);
        assertThat(savedOrder.getDiscountAmount()).isEqualTo(50000);
        assertThat(savedOrder.getFinalAmount()).isEqualTo(950000);

        // 5. 주문에 쿠폰이 연결되어 있는지 확인
        assertThat(savedOrder.getUserCoupon()).isNotNull();
        assertThat(savedOrder.getUserCoupon().getId()).isEqualTo(userCoupon.getId());

        // 6. 주문에 연결된 쿠폰이 사용되었는지 확인
        assertThat(savedOrder.getUserCoupon().isUsed()).isTrue();
        assertThat(savedOrder.getUserCoupon().getUsedAt()).isNotNull();

        // 7. 상품 재고 확인 (10 - 2 = 8)
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(8);

        // 8. 포인트 잔액 확인 (1,000,000 - 950,000 = 50,000)
        PointWallet updatedPointWallet = pointWalletRepository.findById(pointWallet.getId()).orElseThrow();
        assertThat(updatedPointWallet.getBalance()).isEqualTo(50000);
    }

    /**
     * Category 생성 헬퍼 메서드 (Reflection 사용)
     */
    private Category createCategory(String name, String code) {
        Category category = new Category();
        setField(category, "name", name);
        setField(category, "code", code);
        return category;
    }

    /**
     * User 생성 헬퍼 메서드 (Reflection 사용)
     */
    private User createUser(String username, String email, String password) {
        User user = new User();
        setField(user, "username", username);
        setField(user, "email", email);
        setField(user, "password", password);
        setField(user, "registerAt", LocalDateTime.now());
        return user;
    }

    /**
     * Reflection을 사용하여 private 필드에 값을 설정하는 헬퍼 메서드
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("필드 설정 실패: " + fieldName, e);
        }
    }
}