<template>
  <div v-if="columns.length" class="table-toolbar">
    <div class="table-toolbar-actions">
      <el-popover trigger="click" placement="bottom-end" width="220">
        <template #reference>
          <el-button
            class="table-toolbar-action"
            :icon="Grid"
            title="配置表格列"
            aria-label="配置表格列"
          >
            列设置
          </el-button>
        </template>
        <div class="table-column-popover">
          <div class="table-column-popover-head">
            <strong>显示列</strong>
            <el-button text type="primary" @click="resetColumns">重置</el-button>
          </div>
          <el-checkbox
            v-for="column in columns"
            :key="column.key"
            :model-value="column.visible !== false"
            :disabled="column.required"
            @change="toggleColumn(column.key, Boolean($event))"
          >
            {{ column.label }}
          </el-checkbox>
        </div>
      </el-popover>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Grid } from '@element-plus/icons-vue'
import type { TableColumnOption } from './tableColumns'
import { resetTableColumns, toggleTableColumn } from './tableColumns'

const props = withDefaults(defineProps<{
  columns?: TableColumnOption[]
}>(), {
  columns: () => []
})

const emit = defineEmits<{
  'update:columns': [value: TableColumnOption[]]
}>()

function toggleColumn(key: string, visible: boolean) {
  emit('update:columns', toggleTableColumn(props.columns, key, visible))
}

function resetColumns() {
  emit('update:columns', resetTableColumns(props.columns))
}
</script>
