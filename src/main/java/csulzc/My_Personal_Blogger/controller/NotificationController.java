package csulzc.My_Personal_Blogger.controller;

import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.dto.notification.NotificationCountDTO;
import csulzc.My_Personal_Blogger.api.dto.notification.NotificationDTO;
import csulzc.My_Personal_Blogger.api.response.Result;
import csulzc.My_Personal_Blogger.service.business.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "站内通知", description = "通知列表、未读数、标记已读")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "获取我的通知列表", description = "按创建时间倒序，需要登录")
    public ResponseEntity<Result<PageResponseDTO<NotificationDTO>>> listNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new IllegalArgumentException("页码不能为负数");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("每页大小必须在1-100之间");
        }
        return ResponseEntity.ok(Result.success(notificationService.listNotifications(page, size)));
    }

    @GetMapping("/count")
    @Operation(summary = "获取未读数量 + 总数", description = "头部铃铛红点使用")
    public ResponseEntity<Result<NotificationCountDTO>> getCounts() {
        return ResponseEntity.ok(Result.success(notificationService.getCounts()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记单条已读")
    public ResponseEntity<Result<Void>> markAsRead(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("通知ID无效");
        }
        notificationService.markAsRead(id);
        return ResponseEntity.ok(Result.success(null, "已标记为已读"));
    }

    @PostMapping("/read-batch")
    @Operation(summary = "批量标记已读", description = "请求体 {\"ids\": [1,2,3]}")
    public ResponseEntity<Result<Integer>> batchMarkAsRead(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        return ResponseEntity.ok(Result.success(
                notificationService.batchMarkAsRead(ids), "批量标记成功"));
    }

    @PostMapping("/read-all")
    @Operation(summary = "全部标记为已读")
    public ResponseEntity<Result<Integer>> markAllAsRead() {
        return ResponseEntity.ok(Result.success(
                notificationService.markAllAsRead(), "全部标记为已读"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除单条通知")
    public ResponseEntity<Result<Void>> deleteNotification(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("通知ID无效");
        }
        notificationService.deleteNotification(id);
        return ResponseEntity.ok(Result.success(null, "删除成功"));
    }
}
