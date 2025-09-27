<template>
  <button class="panel-toggle" @click="handleToggle">
    <i class="fas fa-sliders-h"></i>
  </button>
</template>

<script setup>
import { defineEmits } from 'vue'

const emit = defineEmits(['toggle'])

const handleToggle = () => {
  emit('toggle')
}
</script>

<style scoped>
.panel-toggle {
  position: absolute;
  right: 10px;
  top: 10px;
  z-index: 30;
  background-color: var(--background-white);
  border: none;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  box-shadow: var(--shadow-md);
  cursor: pointer;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-normal);
  display: none;
}

.panel-toggle:hover {
  transform: scale(1.1);
}

@media (max-width: 1024px) {
  .panel-toggle {
    display: flex;
  }
}
</style>