package com.wrb.devica.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


@WebMvcTest(LaptopController.class)
class LaptopControllerTest {

    private static final String PATH = "/api/laptops";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LaptopService laptopService;

    @Test
    void 최소_가격이_최대_가격보다_크면_400과_이유를_응답한다() throws Exception {
        mockMvc.perform(get(PATH + "?minPrice=2000000&maxPrice=1000000"))
            .andExpect(status().isBadRequest())
            .andExpect(content().json("""
                {"message": "최소 가격은 최대 가격보다 클 수 없습니다.", "code": "INVALID_REQUEST"}
                """, true));
    }

}
