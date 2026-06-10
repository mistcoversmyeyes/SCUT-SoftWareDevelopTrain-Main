package com.scut.wms.masterdata;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MasterDataControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void optionsReturnsInboundMasterData() throws Exception {
        mockMvc.perform(get("/api/master-data/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suppliers[0].code").value("8KH"))
                .andExpect(jsonPath("$.materials[0].code").value("5HG 807 109 C"))
                .andExpect(jsonPath("$.warehouses[0].code").value("WH-JY"))
                .andExpect(jsonPath("$.locations[0].warehouseId").value(1))
                .andExpect(jsonPath("$.locations[0].code").value("A-01"));
    }
}
