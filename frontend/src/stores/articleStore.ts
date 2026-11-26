/**
 * 文章全局 store。
 * - 缓存已加载的文章交互状态，避免首页/分类页重复请求
 * - 点赞状态跨页面同步（详情页点赞后，列表卡片上的点赞数也能立即更新）
 */
import { create } from 'zustand'
import type { ArticleListItemDTO } from '@/types'

/** 列表项与详情页共有的交互快照字段（列表项本身不含 isLiked） */
export interface ArticleInteraction {
  id: number
  likeCount?: number
  isLiked?: boolean
  commentCount?: number
}

interface ArticleState {
  /** 按 articleId 缓存的文章交互状态（用于点赞数同步等场景） */
  articleMap: Record<number, ArticleInteraction>

  /** 更新单篇文章的局部字段（点赞后调用） */
  patchArticle: (id: number, patch: Partial<Omit<ArticleInteraction, 'id'>>) => void
  /** 批量写入文章列表（拉取列表后调用）；不覆盖已有的 isLiked */
  hydrateList: (list: ArticleListItemDTO[]) => void
}

export const useArticleStore = create<ArticleState>((set) => ({
  articleMap: {},

  patchArticle: (id, patch) => {
    set((s) => {
      const existing = s.articleMap[id]
      return {
        articleMap: {
          ...s.articleMap,
          [id]: { ...(existing ?? { id }), ...patch },
        },
      }
    })
  },

  hydrateList: (list) => {
    set((s) => {
      const next = { ...s.articleMap }
      for (const a of list) {
        if (a.id == null) continue
        const existing = next[a.id]
        next[a.id] = {
          id: a.id,
          likeCount: a.likeCount,
          commentCount: a.commentCount,
          // 列表接口不返回 isLiked，保留已有值避免覆盖详情页刚同步的状态
          isLiked: existing?.isLiked,
        }
      }
      return { articleMap: next }
    })
  },
}))
