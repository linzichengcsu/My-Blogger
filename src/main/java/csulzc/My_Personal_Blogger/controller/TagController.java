package csulzc.My_Personal_Blogger.controller;

import csulzc.My_Personal_Blogger.api.dto.tag.TagDTO;
import csulzc.My_Personal_Blogger.api.dto.tag.TagRequest;
import csulzc.My_Personal_Blogger.api.response.Result;
import csulzc.My_Personal_Blogger.service.business.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@Tag(name = "标签管理", description = "文章标签CRUD相关接口")
public class TagController {

    private final TagService tagService;

    /**
     * 获取全部标签（公开，供写作页选择器与标签云）
     */
    @GetMapping
    @Operation(summary = "获取全部标签", description = "公开访问，返回所有标签及其文章数")
    public ResponseEntity<Result<List<TagDTO>>> getAllTags() {
        List<TagDTO> tags = tagService.getAllTags();
        return ResponseEntity.ok(Result.success(tags));
    }

    /**
     * 获取标签详情（通过 ID）
     */
    @GetMapping("/{tagId}")
    @Operation(summary = "获取标签详情", description = "公开访问，获取指定ID的标签详情")
    public ResponseEntity<Result<TagDTO>> getTagById(@PathVariable Long tagId) {
        if (tagId == null || tagId <= 0) {
            throw new IllegalArgumentException("标签ID无效");
        }
        TagDTO tag = tagService.getTagById(tagId);
        return ResponseEntity.ok(Result.success(tag));
    }

    /**
     * 创建标签
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "创建标签", description = "创建一个新的标签")
    public ResponseEntity<Result<TagDTO>> createTag(@Valid @RequestBody TagRequest request) {
        TagDTO tag = tagService.createTag(request);
        return ResponseEntity.ok(Result.success(tag, "标签创建成功"));
    }

    /**
     * 更新标签
     */
    @PutMapping("/{tagId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "更新标签", description = "更新指定ID的标签")
    public ResponseEntity<Result<TagDTO>> updateTag(
            @PathVariable Long tagId,
            @Valid @RequestBody TagRequest request) {
        if (tagId == null || tagId <= 0) {
            throw new IllegalArgumentException("标签ID无效");
        }
        TagDTO tag = tagService.updateTag(tagId, request);
        return ResponseEntity.ok(Result.success(tag, "标签更新成功"));
    }

    /**
     * 删除标签（仍被文章引用时拒绝删除）
     */
    @DeleteMapping("/{tagId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "删除标签", description = "删除指定ID的标签；仍有文章引用时不允许删除")
    public ResponseEntity<Result<Void>> deleteTag(@PathVariable Long tagId) {
        if (tagId == null || tagId <= 0) {
            throw new IllegalArgumentException("标签ID无效");
        }
        tagService.deleteTag(tagId);
        return ResponseEntity.ok(Result.success(null, "标签删除成功"));
    }

    /**
     * 获取标签总数
     */
    @GetMapping("/stats/total")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取标签总数", description = "获取标签的总数")
    public ResponseEntity<Result<Long>> getTotalTagCount() {
        long count = tagService.getTotalTagCount();
        return ResponseEntity.ok(Result.success(count));
    }
}
