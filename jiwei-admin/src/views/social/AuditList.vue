<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>内容审核</span>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="评价审核" name="review">
          <el-table :data="reviews" stripe v-loading="reviewLoading">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="userId" label="用户ID" width="90" />
            <el-table-column prop="dishId" label="菜品ID" width="90" />
            <el-table-column label="评分" width="180">
              <template #default="{ row }">
                <el-rate :model-value="row.rating" disabled show-score />
              </template>
            </el-table-column>
            <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
            <el-table-column label="标签" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.tags" size="small" type="info">{{ row.tags }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="likes" label="点赞数" width="90" />
            <el-table-column label="审核状态" width="100">
              <template #default="{ row }">
                <el-tag :type="auditStatusTag(row.auditStatus)">{{ auditStatusLabel(row.auditStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <template v-if="row.auditStatus === 0">
                  <el-button type="success" size="small" @click="handleReviewPass(row.id)">通过</el-button>
                  <el-button type="danger" size="small" @click="handleReviewReject(row.id)">驳回</el-button>
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="reviewPage"
            v-model:page-size="reviewPageSize"
            :total="reviewTotal"
            layout="total, prev, pager, next"
            @current-change="fetchReviews"
            style="margin-top: 20px; justify-content: center;"
          />
        </el-tab-pane>

        <el-tab-pane label="笔记审核" name="note">
          <el-table :data="notes" stripe v-loading="noteLoading">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="userId" label="作者ID" width="90" />
            <el-table-column prop="title" label="标题" width="180" show-overflow-tooltip />
            <el-table-column label="内容" min-width="200">
              <template #default="{ row }">
                {{ truncate(row.content, 40) }}
              </template>
            </el-table-column>
            <el-table-column label="点赞/评论/收藏" width="120">
              <template #default="{ row }">
                {{ row.likeCount ?? 0 }}/{{ row.commentCount ?? 0 }}/{{ row.collectCount ?? 0 }}
              </template>
            </el-table-column>
            <el-table-column label="审核状态" width="100">
              <template #default="{ row }">
                <el-tag :type="auditStatusTag(row.auditStatus)">{{ auditStatusLabel(row.auditStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <template v-if="row.auditStatus === 0">
                  <el-button type="success" size="small" @click="handleNotePass(row.id)">通过</el-button>
                  <el-button type="danger" size="small" @click="handleNoteReject(row.id)">驳回</el-button>
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="notePage"
            v-model:page-size="notePageSize"
            :total="noteTotal"
            layout="total, prev, pager, next"
            @current-change="fetchNotes"
            style="margin-top: 20px; justify-content: center;"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getReviewAuditPage, auditReview, getNoteAuditPage, auditNote } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

// ========== Tab ==========
const activeTab = ref('review')

// ========== Review state ==========
const reviewLoading = ref(false)
const reviews = ref<any[]>([])
const reviewPage = ref(1)
const reviewPageSize = ref(10)
const reviewTotal = ref(0)

// ========== Note state ==========
const noteLoading = ref(false)
const notes = ref<any[]>([])
const notePage = ref(1)
const notePageSize = ref(10)
const noteTotal = ref(0)

// ========== Audit status helpers ==========
const auditStatusMap: Record<number, string> = {
  0: '待审核',
  1: '通过',
  2: '驳回'
}

function auditStatusLabel(status: number) {
  return auditStatusMap[status] ?? '未知'
}

function auditStatusTag(status: number): 'warning' | 'success' | 'danger' | 'info' {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

// ========== Content helpers ==========
function truncate(text: string, max: number) {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '...' : text
}

// ========== Review operations ==========
async function fetchReviews() {
  reviewLoading.value = true
  try {
    const res = await getReviewAuditPage({ page: reviewPage.value, pageSize: reviewPageSize.value })
    reviews.value = res.data.records ?? []
    reviewTotal.value = res.data.total ?? 0
  } finally {
    reviewLoading.value = false
  }
}

async function handleReviewPass(id: number) {
  await auditReview(id, 1)
  ElMessage.success('审核通过')
  fetchReviews()
}

async function handleReviewReject(id: number) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回审核', {
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    reason = value ?? ''
  } catch {
    return
  }
  await auditReview(id, 2, reason)
  ElMessage.success('已驳回')
  fetchReviews()
}

// ========== Note operations ==========
async function fetchNotes() {
  noteLoading.value = true
  try {
    const res = await getNoteAuditPage({ page: notePage.value, pageSize: notePageSize.value })
    notes.value = res.data.records ?? []
    noteTotal.value = res.data.total ?? 0
  } finally {
    noteLoading.value = false
  }
}

async function handleNotePass(id: number) {
  await auditNote(id, 1)
  ElMessage.success('审核通过')
  fetchNotes()
}

async function handleNoteReject(id: number) {
  try {
    await ElMessageBox.prompt('请输入驳回原因', '驳回审核', {
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await auditNote(id, 2)
  ElMessage.success('已驳回')
  fetchNotes()
}

// ========== Tab change ==========
function handleTabChange(name: string | number) {
  if (name === 'review' && reviews.value.length === 0) {
    fetchReviews()
  } else if (name === 'note' && notes.value.length === 0) {
    fetchNotes()
  }
}

onMounted(fetchReviews)
</script>

<style scoped>
.page-container { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
