<template>
  <div class="field">
    <label class="label">{{ label }} · one entry per line</label>
    <textarea :value="modelValue.join('\n')" class="textarea" required @input="update" />
  </div>
</template>

<script setup lang="ts">
defineProps<{ label: string; modelValue: string[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()
function update(event: Event): void {
  const value = (event.target as HTMLTextAreaElement).value
  emit('update:modelValue', value.split('\n').map((item) => item.trim()).filter(Boolean))
}
</script>
