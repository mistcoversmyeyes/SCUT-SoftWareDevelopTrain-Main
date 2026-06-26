package com.scut.wms.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryOverviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void overviewReturnsLowStockThresholdAndShortageFlag() throws Exception {
        mockMvc.perform(get("/api/inventory/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suppliers[*].materials[?(@.code == '5HG.807.109.C')].lowStockQty").value(hasItem(80.0)))
                .andExpect(jsonPath("$.suppliers[*].materials[?(@.code == '5HG.807.109.C')].shortage").value(hasItem(false)))
                .andExpect(jsonPath("$.suppliers[*].materials[?(@.code == '5WA.857.031.B')].lowStockQty").value(hasItem(30.0)))
                .andExpect(jsonPath("$.suppliers[*].materials[?(@.code == '5WA.857.031.B')].shortage").value(hasItem(true)));
    }
}
