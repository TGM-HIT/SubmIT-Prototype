<template>
  <section class="page stack">
    <p class="page-intro">{{ $t('templates.intro') }}</p>
    <div class="panel stack">
      <h2 class="section-title">{{ $t('templates.ideas') }}</h2>
      <button class="button button-secondary" type="button" @click="download(ideaEnglish, 'idea-template-en.md')">{{ $t('templates.ideaEnglish') }}</button>
      <button class="button button-secondary" type="button" @click="download(ideaGerman, 'idea-template-de.md')">{{ $t('templates.ideaGerman') }}</button>
    </div>
    <div class="panel stack">
      <h2 class="section-title">{{ $t('templates.specifications') }}</h2>
      <button class="button button-secondary" type="button" @click="download(specificationEnglish, 'specification-template-en.md')">{{ $t('templates.specificationEnglish') }}</button>
      <button class="button button-secondary" type="button" @click="download(specificationGerman, 'specification-template-de.md')">{{ $t('templates.specificationGerman') }}</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import ideaEnglish from './idea-template-en.md?raw'
import ideaGerman from './idea-template-de.md?raw'
import specificationEnglish from './specification-template-en.md?raw'
import specificationGerman from './specification-template-de.md?raw'
import { downloadBlob } from '../../api/client'

function download(content: string, filename: string): void {
  downloadBlob(new Blob([content], { type: 'text/markdown;charset=utf-8' }), filename)
}
</script>
