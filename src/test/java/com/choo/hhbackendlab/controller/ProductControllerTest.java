package com.choo.hhbackendlab.controller;

import com.choo.hhbackendlab.entity.Category;
import com.choo.hhbackendlab.entity.Product;
import com.choo.hhbackendlab.usecase.product.AddProductStockUseCase;
import com.choo.hhbackendlab.usecase.product.CreateProductUseCase;
import com.choo.hhbackendlab.usecase.product.GetProductStockUseCase;
import com.choo.hhbackendlab.usecase.product.RemoveProductStockUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
public class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateProductUseCase createProductUseCase;

    @MockitoBean
    private GetProductStockUseCase getProductStockUseCase;

    @MockitoBean
    private RemoveProductStockUseCase removeProductStockUseCase;

    @MockitoBean
    private AddProductStockUseCase addProductStockUseCase;

    @Test
    @DisplayName("상품 등록 성공")
    void createProduct_Success() throws Exception {
        // given
        String requestBody = """
                {
                    "name": "laptop",
                    "description": "노트북입니다.",
                    "price": 100000,
                    "stock": 10,
                    "categoryId": 1
                }
                """;

        Category category = new Category();
        Product product = new Product("laptop", "노트북입니다.", category, 100000, 10);

        given(createProductUseCase.createProduct(any())).willReturn(product);

        // when & then
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("laptop"))
                .andExpect(jsonPath("$.description").value("노트북입니다."))
                .andExpect(jsonPath("$.price").value(100000))
                .andExpect(jsonPath("$.stock").value(10));
    }

    @Test
    @DisplayName("재고 조회 성공")
    void getStock_Success() throws Exception {
        // given
        Long productId = 1L;
        int stock = 15;

        given(getProductStockUseCase.getStock(productId)).willReturn(stock);

        // when & then
        mockMvc.perform(get("/api/products/{productId}/stock", productId))
                .andExpect(status().isOk())
                .andExpect(content().string("15"));
    }

    @Test
    @DisplayName("재고 차감 성공")
    void removeStock_Success() throws Exception {
        // given
        Long productId = 1L;
        int quantity = 5;

        doNothing().when(removeProductStockUseCase).removeProductStock(productId, quantity);

        // when & then
        mockMvc.perform(patch("/api/products/{productId}/stock/remove", productId)
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isOk())
                .andExpect(content().string("재고가 5개 차감되었습니다."));
    }

    @Test
    @DisplayName("재고 차감 실패 - 재고 부족")
    void removeStock_InsufficientStock() throws Exception {
        // given
        Long productId = 1L;
        int quantity = 100;

        doThrow(new IllegalArgumentException("재고가 부족합니다."))
                .when(removeProductStockUseCase).removeProductStock(productId, quantity);

        // when & then
        mockMvc.perform(patch("/api/products/{productId}/stock/remove", productId)
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("재고 적재 성공")
    void addStock_Success() throws Exception {
        // given
        Long productId = 1L;
        int quantity = 10;

        doNothing().when(addProductStockUseCase).addProductStock(productId, quantity);

        // when & then
        mockMvc.perform(patch("/api/products/{productId}/stock/add", productId)
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isOk())
                .andExpect(content().string("재고가 10개 적재되었습니다."));
    }
}
