package csulzc.My_Personal_Blogger.service.business;

import csulzc.My_Personal_Blogger.api.dto.tag.TagDTO;
import csulzc.My_Personal_Blogger.api.dto.tag.TagRequest;
import csulzc.My_Personal_Blogger.domain.entity.ArticleTag;
import csulzc.My_Personal_Blogger.domain.entity.Tag;
import csulzc.My_Personal_Blogger.repository.ArticleTagRepository;
import csulzc.My_Personal_Blogger.repository.TagRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private static final int MAX_TAG_NAME_LENGTH = 30;

    private final TagRepository tagRepository;
    private final ArticleTagRepository articleTagRepository;

    /**
     * 标签查询上下文：一次加载文章数聚合与全量标签映射，
     * 供各查询方法在内存中组装 DTO，避免逐标签懒加载（N+1）。
     */
    private record TagContext(
            Map<Long, Long> articleCounts,   // tagId -> 文章数（含 0）
            Map<Long, Tag> tagsById) {       // tagId -> 标签实体
    }

    private TagContext loadContext() {
        Map<Long, Long> articleCounts = tagRepository.findAllWithArticleCount().stream()
                .collect(Collectors.toMap(
                        obj -> ((Tag) obj[0]).getId(),
                        obj -> ((Number) obj[1]).longValue(),
                        (a, b) -> a));
        Map<Long, Tag> tagsById = tagRepository.findAll().stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));
        return new TagContext(articleCounts, tagsById);
    }

    // ==================== 标签查询 ====================

    /**
     * 获取全部标签（含文章数，按名称排序），供写作页标签选择器与标签云使用。
     */
    @Cacheable(cacheNames = "tag:list", sync = true)
    public List<TagDTO> getAllTags() {
        TagContext ctx = loadContext();
        return ctx.tagsById().values().stream()
                .sorted(Comparator.comparing(Tag::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(t -> convertToDTO(t, ctx))
                .collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取标签详情。
     */
    @Cacheable(cacheNames = "tag:detail", key = "#id", sync = true)
    public TagDTO getTagById(Long id) {
        TagContext ctx = loadContext();
        Tag tag = ctx.tagsById().get(id);
        if (tag == null) {
            throw new EntityNotFoundException("标签不存在");
        }
        return convertToDTO(tag, ctx);
    }

    /**
     * 标签总数（管理端统计）。
     */
    @Cacheable(cacheNames = "tag:list", sync = true)
    public long getTotalTagCount() {
        return tagRepository.count();
    }

    // ==================== 标签增删改 ====================

    @CacheEvict(cacheNames = "tag:list", allEntries = true)
    @Transactional(timeout = 30)
    public TagDTO createTag(TagRequest request) {
        String name = normalizeName(request.getName());
        if (tagRepository.existsByName(name)) {
            throw new IllegalArgumentException("标签名称已存在");
        }
        Tag saved = tagRepository.save(Tag.builder().name(name).build());
        return convertToDTO(saved, loadContext());
    }

    @CacheEvict(cacheNames = {"tag:detail", "tag:list"}, allEntries = true)
    @Transactional(timeout = 30)
    public TagDTO updateTag(Long id, TagRequest request) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("标签不存在"));
        String name = normalizeName(request.getName());
        tagRepository.findByName(name)
                .filter(t -> !t.getId().equals(id))
                .ifPresent(t -> {
                    throw new IllegalArgumentException("标签名称已存在");
                });
        tag.setName(name);
        Tag saved = tagRepository.save(tag);
        return convertToDTO(saved, loadContext());
    }

    /**
     * 删除标签：仍被文章引用时拒绝删除（避免关联文章出现悬空引用与缓存不一致）。
     */
    @CacheEvict(cacheNames = {"tag:detail", "tag:list"}, allEntries = true)
    @Transactional(timeout = 30)
    public void deleteTag(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("标签不存在"));
        if (articleTagRepository.countByTagId(id) > 0) {
            throw new IllegalStateException("该标签下还有文章，无法删除");
        }
        tagRepository.delete(tag);
    }

    // ==================== 文章-标签关联 ====================

    /**
     * 获取单篇文章的标签名称列表（详情页 DTO 组装用）。
     */
    public List<String> getTagNamesByArticleId(Long articleId) {
        return tagRepository.findTagMappingByArticleIds(List.of(articleId)).stream()
                .map(row -> ((Tag) row[1]).getName())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /**
     * 批量获取多篇文章的标签名称映射：articleId -> 标签名列表（列表 DTO 组装用，避免 N+1）。
     */
    public Map<Long, List<String>> getTagNamesByArticleIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, List<String>> map = new HashMap<>();
        for (Object[] row : tagRepository.findTagMappingByArticleIds(articleIds)) {
            Long articleId = (Long) row[0];
            String name = ((Tag) row[1]).getName();
            map.computeIfAbsent(articleId, k -> new ArrayList<>()).add(name);
        }
        map.values().forEach(list -> list.sort(String.CASE_INSENSITIVE_ORDER));
        return map;
    }

    /**
     * 重建某篇文章的标签关联：先清空旧关联，再按名称解析（不存在则自动创建）后写入。
     * 返回去重、排序后的最终标签名列表。传 null 不修改，传空集合表示清空。
     */
    @CacheEvict(cacheNames = "tag:list", allEntries = true)
    @Transactional(timeout = 30)
    public List<String> saveArticleTags(Long articleId, List<String> tagNames) {
        articleTagRepository.deleteByArticleId(articleId);

        List<String> normalized = normalizeNames(tagNames);
        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Tag> tags = resolveOrCreateTags(normalized);
        List<ArticleTag> links = tags.stream()
                .map(t -> ArticleTag.builder().articleId(articleId).tagId(t.getId()).build())
                .collect(Collectors.toList());
        articleTagRepository.saveAll(links);

        return tags.stream()
                .map(Tag::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    /**
     * 按名称批量解析标签：已存在的直接取出，不存在的自动创建（并发下唯一约束冲突时回退查询）。
     */
    private Set<Tag> resolveOrCreateTags(List<String> names) {
        Set<Tag> result = new LinkedHashSet<>();
        Map<String, Tag> byName = tagRepository.findByNameIn(names).stream()
                .collect(Collectors.toMap(Tag::getName, t -> t, (a, b) -> a));
        for (String name : names) {
            Tag tag = byName.get(name);
            if (tag == null) {
                tag = createTagIfAbsent(name);
                byName.put(name, tag);
            }
            result.add(tag);
        }
        return result;
    }

    private Tag createTagIfAbsent(String name) {
        try {
            return tagRepository.saveAndFlush(Tag.builder().name(name).build());
        } catch (DataIntegrityViolationException e) {
            // 并发创建同名标签：唯一约束冲突，回退到查询已有记录
            return tagRepository.findByName(name)
                    .orElseThrow(() -> new IllegalStateException("标签创建失败: " + name, e));
        }
    }

    private List<String> normalizeNames(List<String> names) {
        if (names == null) {
            return new ArrayList<>();
        }
        return names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.length() > MAX_TAG_NAME_LENGTH ? s.substring(0, MAX_TAG_NAME_LENGTH) : s)
                .distinct()
                .collect(Collectors.toList());
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("标签名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("标签名称不能为空");
        }
        if (trimmed.length() > MAX_TAG_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_TAG_NAME_LENGTH);
        }
        return trimmed;
    }

    private TagDTO convertToDTO(Tag tag, TagContext ctx) {
        return TagDTO.builder()
                .id(tag.getId())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .name(tag.getName())
                .articleCount(ctx.articleCounts().getOrDefault(tag.getId(), 0L).intValue())
                .build();
    }
}
