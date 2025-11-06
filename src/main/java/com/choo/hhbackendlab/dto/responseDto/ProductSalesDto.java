package com.choo.hhbackendlab.dto.responseDto;

import com.choo.hhbackendlab.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductSalesDto {
    private Product product;
    private Long salesCount;  // 판매량

    public ProductSalesDto(Long productId, String name, String description, int price, int stock, long viewCount, Long salesCount) {
        this.salesCount = salesCount;
    }
}
