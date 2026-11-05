// 后台列表批量勾选（全站通用）：全选/半选状态 + 单行/整页勾选
// 用法：const { selectedIds, isAllSelected, isIndeterminate, toggleSelect, toggleSelectAll, clearSelection } = useBatchSelect(list, x => x.userId)
import { ref, computed } from 'vue'

export function useBatchSelect(listRef, keyOf = (x) => x) {
  const selectedIds = ref([])

  const isAllSelected = computed(() =>
    listRef.value.length > 0 && selectedIds.value.length === listRef.value.length
  )
  const isIndeterminate = computed(() =>
    selectedIds.value.length > 0 && selectedIds.value.length < listRef.value.length
  )
  const toggleSelect = (id) => {
    const idx = selectedIds.value.indexOf(id)
    if (idx >= 0) selectedIds.value.splice(idx, 1)
    else selectedIds.value.push(id)
  }
  const toggleSelectAll = (val) => {
    selectedIds.value = val ? listRef.value.map(keyOf) : []
  }
  const clearSelection = () => { selectedIds.value = [] }

  return { selectedIds, isAllSelected, isIndeterminate, toggleSelect, toggleSelectAll, clearSelection }
}
