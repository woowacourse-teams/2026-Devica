package com.wrb.devica.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LaptopController.class)
class LaptopControllerTest {

    private static final String PATH = "/api/laptops";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LaptopService laptopService;

    @BeforeEach
    void setUp() {
        given(laptopService.findLaptops(any(), anyInt(), anyInt()))
            .willAnswer(invocation -> new SliceImpl<>(
                List.of(),
                PageRequest.of(invocation.getArgument(1), invocation.getArgument(2)),
                false));
    }

    @Test
    void 파라미터가_없을_때_목록을_조회하면_200을_반환한다() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "os=WINDOWS",
            "os=MAC",
            "cpuScore=1",
            "memoryGb=1&storageGb=1",
            "page=0&size=1",
            "size=100",
            "os=",
            "os=MAC&cpuScore=15000&memoryGb=16&storageGb=512&page=2&size=50"
    })
    void 유효한_파라미터일_때_목록을_조회하면_200을_반환한다(String query) throws Exception {
        mockMvc.perform(get(PATH + "?" + query))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "cpuScore=abc",
            "memoryGb=16.5",
            "page=one",
            "size=twenty"
    })
    void 파라미터_타입이_맞지_않을_때_목록을_조회하면_400을_반환한다(String query) throws Exception {
        mockMvc.perform(get(PATH + "?" + query))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "os=LINUX",
            "os=windows",
            "os=Mac"
    })
    void os가_허용값이_아닐_때_목록을_조회하면_400을_반환한다(String query) throws Exception {
        mockMvc.perform(get(PATH + "?" + query))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "cpuScore=0",
            "cpuScore=-1",
            "memoryGb=0",
            "memoryGb=-8",
            "storageGb=0",
            "storageGb=-256"
    })
    void 사양_값이_1_미만일_때_목록을_조회하면_400을_반환한다(String query) throws Exception {
        mockMvc.perform(get(PATH + "?" + query))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=-100"})
    void page가_음수일_때_목록을_조회하면_400을_반환한다(String query) throws Exception {
        mockMvc.perform(get(PATH + "?" + query))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=0", "size=-1", "size=101", "size=1000"})
    void size가_허용_범위를_벗어날_때_목록을_조회하면_400을_반환한다(String query) throws Exception {
        mockMvc.perform(get(PATH + "?" + query))
                .andExpect(status().isBadRequest());
    }
}
