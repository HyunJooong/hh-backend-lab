package com.choo.hhbackendlab.usecase.product;

import com.choo.hhbackendlab.entity.Category;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class GetTopProductsBySalesUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private GetTopProductsBySalesUseCase getTopProductsBySalesUseCase;

    @Test
    @DisplayName("최근 7일 판매량 기준 인기 상품 조회 성공")
    void getTopProductsBySales_Success() {
        // given
        int limit = 3;
        Category category = new Category();

        Product product1 = new Product("laptop", "노트북", category, 100000, 10);
        Product product2 = new Product("mouse", "마우스", category, 30000, 20);
        Product product3 = new Product("keyboard", "키보드", category, 50000, 15);

        List<Product> mockProducts = Arrays.asList(product1, product2, product3);

        Pageable pageable = PageRequest.of(0, limit);
        given(productRepository.findTopProductsBySalesInLastWeek(any(LocalDateTime.class), eq(pageable)))
                .willReturn(mockProducts);

        // when
        List<Product> result = getTopProductsBySalesUseCase.getTopProductsBySales(limit);

        // then
        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(product1, product2, product3);
        verify(productRepository).findTopProductsBySalesInLastWeek(any(LocalDateTime.class), any(Pageable.class));
    }

    @Test
    @DisplayName("최근 7일 판매량 기준 인기 상품 조회 실패 - 잘못된 limit 값")
    void getTopProductsBySales_InvalidLimit() {
        // given
        int invalidLimit = 0;

        // when & then
        assertThatThrownBy(() -> getTopProductsBySalesUseCase.getTopProductsBySales(invalidLimit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("조회할 상품 개수는 1개 이상이어야 합니다");
    }

    @Test
    @DisplayName("최근 7일 판매량 기준 인기 상품 조회 - 빈 목록 반환")
    void getTopProductsBySales_EmptyList() {
        // given
        int limit = 10;
        Pageable pageable = PageRequest.of(0, limit);

        given(productRepository.findTopProductsBySalesInLastWeek(any(LocalDateTime.class), eq(pageable)))
                .willReturn(Arrays.asList());

        // when
        List<Product> result = getTopProductsBySalesUseCase.getTopProductsBySales(limit);

        // then
        assertThat(result).isEmpty();
        verify(productRepository).findTopProductsBySalesInLastWeek(any(LocalDateTime.class), any(Pageable.class));
    }

    @Test
    @DisplayName("최근 7일 판매량 기준 인기 상품 조회 - 요청한 개수보다 적은 상품")
    void getTopProductsBySales_LessThanLimit() {
        // given
        int limit = 10;
        Category category = new Category();

        Product product1 = new Product("laptop", "노트북", category, 100000, 10);
        Product product2 = new Product("mouse", "마우스", category, 30000, 20);

        List<Product> mockProducts = Arrays.asList(product1, product2);

        Pageable pageable = PageRequest.of(0, limit);
        given(productRepository.findTopProductsBySalesInLastWeek(any(LocalDateTime.class), eq(pageable)))
                .willReturn(mockProducts);

        // when
        List<Product> result = getTopProductsBySalesUseCase.getTopProductsBySales(limit);

        // then
        assertThat(result).hasSize(2);
        verify(productRepository).findTopProductsBySalesInLastWeek(any(LocalDateTime.class), any(Pageable.class));
    }

    @Test
    @DisplayName("최근 7일 판매량 기준 인기 상품 조회 - 7일 기준 검증")
    void getTopProductsBySales_WeekAgoParameter() {
        // given
        int limit = 5;
        Category category = new Category();
        Product product = new Product("laptop", "노트북", category, 100000, 10);

        Pageable pageable = PageRequest.of(0, limit);
        given(productRepository.findTopProductsBySalesInLastWeek(any(LocalDateTime.class), eq(pageable)))
                .willReturn(Arrays.asList(product));

        // when
        LocalDateTime beforeCall = LocalDateTime.now().minusDays(7);
        List<Product> result = getTopProductsBySalesUseCase.getTopProductsBySales(limit);
        LocalDateTime afterCall = LocalDateTime.now().minusDays(7);

        // then
        assertThat(result).hasSize(1);
        // weekAgo 파라미터가 현재로부터 7일 전 시간으로 전달되었는지 검증
        verify(productRepository).findTopProductsBySalesInLastWeek(any(LocalDateTime.class), any(Pageable.class));
    }
}
