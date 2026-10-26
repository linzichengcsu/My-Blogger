package csulzc.My_Personal_Blogger.controller;

import csulzc.My_Personal_Blogger.api.dto.common.ApiResponse;
import csulzc.My_Personal_Blogger.api.dto.dashboard.DashboardStatsDTO;
import csulzc.My_Personal_Blogger.service.business.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "管理员接口")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard/stats")
    @Operation(summary = "获取看板统计数据", description = "仅管理员可访问")
    public ApiResponse<DashboardStatsDTO> getDashboardStats() {
        DashboardStatsDTO stats = adminService.getDashboardStats();
        return ApiResponse.success(stats);
    }
}
