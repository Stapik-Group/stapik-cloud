package pl.stapik.cloud.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import pl.stapik.cloud.AbstractIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
class DashboardDelegateTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnStatsWithFullThirtyDayWriteSeries() throws Exception {
        // when & then
        mockMvc.perform(get("/api/admin/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentStorageBytes").isNumber())
                .andExpect(jsonPath("$.assetStorageBytes").isNumber())
                .andExpect(jsonPath("$.documentCount").isNumber())
                .andExpect(jsonPath("$.assetCount").isNumber())
                .andExpect(jsonPath("$.apiKeyActivity").isArray())
                .andExpect(jsonPath("$.recentConflicts").isArray())
                .andExpect(jsonPath("$.writesPerDay.length()").value(30));
    }
}
