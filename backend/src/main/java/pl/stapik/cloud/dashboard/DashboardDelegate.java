package pl.stapik.cloud.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pl.stapik.cloud.admin.api.DashboardApiDelegate;
import pl.stapik.cloud.admin.data.DashboardStatsResponse;

@Component
@RequiredArgsConstructor
public class DashboardDelegate implements DashboardApiDelegate {

    private final DashboardService dashboardService;
    private final DashboardMapper dashboardMapper;

    @Override
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(dashboardMapper.toResponse(dashboardService.getStats()));
    }
}
