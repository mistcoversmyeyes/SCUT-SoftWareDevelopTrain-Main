package com.scut.wms.inventory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.inbound.InventoryTag;
import com.scut.wms.inbound.InventoryTagMapper;
import com.scut.wms.lock.InventoryHold;
import com.scut.wms.lock.InventoryHoldMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

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

    @Autowired
    private InventoryTagMapper inventoryTagMapper;

    @Autowired
    private InventoryHoldMapper inventoryHoldMapper;

    @BeforeEach
    void makeMaterialOneOnHandButUnavailable() {
        inventoryHoldMapper.delete(Wrappers.<InventoryHold>lambdaQuery()
                .in(InventoryHold::getInventoryTagId, 1L, 2L, 13L, 14L));

        inventoryTagMapper.update(null, Wrappers.<InventoryTag>lambdaUpdate()
                .in(InventoryTag::getId, 1L, 2L)
                .set(InventoryTag::getStatus, "LOCKED"));
        inventoryTagMapper.update(null, Wrappers.<InventoryTag>lambdaUpdate()
                .eq(InventoryTag::getId, 13L)
                .set(InventoryTag::getStatus, "RECEIVED"));
        inventoryTagMapper.update(null, Wrappers.<InventoryTag>lambdaUpdate()
                .eq(InventoryTag::getId, 14L)
                .set(InventoryTag::getStatus, "SEALED"));

        InventoryHold hold = new InventoryHold();
        hold.setInventoryTagId(13L);
        hold.setHoldType(InventoryHold.MANUAL_LOCK);
        hold.setHoldQty(new BigDecimal("100"));
        hold.setStatus(InventoryHold.ACTIVE);
        hold.setReason("测试可用库存短缺");
        hold.setOperatorName("测试");
        inventoryHoldMapper.insert(hold);
    }

    @Test
    void overviewReturnsAvailableQtyAndBasesShortageOnAvailability() throws Exception {
        mockMvc.perform(get("/api/inventory/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suppliers[*].materials[?(@.code == '5HG.807.109.C')].lowStockQty").value(hasItem(80.0)))
                .andExpect(jsonPath("$.suppliers[*].materials[?(@.code == '5HG.807.109.C')].currentQty").value(hasItem(400.0)))
                .andExpect(jsonPath("$.suppliers[*].materials[?(@.code == '5HG.807.109.C')].availableQty").value(hasItem(0)))
                .andExpect(jsonPath("$.suppliers[*].materials[?(@.code == '5HG.807.109.C')].shortage").value(hasItem(true)))
                .andExpect(jsonPath("$.suppliers[*].materials[?(@.code == '5WA.857.031.B')].lowStockQty").value(hasItem(30.0)))
                .andExpect(jsonPath("$.suppliers[*].materials[?(@.code == '5WA.857.031.B')].availableQty").value(hasItem(0)))
                .andExpect(jsonPath("$.suppliers[*].materials[?(@.code == '5WA.857.031.B')].shortage").value(hasItem(true)));
    }
}
