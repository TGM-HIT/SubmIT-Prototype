import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import ModalDialog from './ModalDialog.vue'

describe('ModalDialog', () => {
  let wrapper: ReturnType<typeof mount> | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('exposes dialog semantics, handles Escape, and restores focus to its trigger', async () => {
    const Host = defineComponent({
      setup() {
        const open = ref(false)
        return () => h('div', [
          h('button', { id: 'trigger', onClick: () => { open.value = true } }, 'Open modal'),
          open.value ? h(ModalDialog, { title: 'Example dialog', onClose: () => { open.value = false } }, {
            default: () => h('button', { id: 'inside' }, 'Inside dialog'),
          }) : null,
        ])
      },
    })
    wrapper = mount(Host, { attachTo: document.body })
    const trigger = wrapper.get('#trigger')
    document.getElementById('trigger')?.focus()
    await trigger.trigger('click')
    await flushPromises()

    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.attributes('aria-labelledby')).toBeTruthy()
    const closeButton = wrapper.get('[aria-label="Close dialog"]').element
    const contentButton = wrapper.get('#inside').element
    expect(document.activeElement).toBe(closeButton)
    expect(document.body.style.overflow).toBe('hidden')
    Object.defineProperty(closeButton, 'getClientRects', { value: () => ({ length: 1 }) })
    Object.defineProperty(contentButton, 'getClientRects', { value: () => ({ length: 1 }) })

    await dialog.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(contentButton)
    await dialog.trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(closeButton)
    await dialog.trigger('keydown', { key: 'Escape' })
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger.element)
    expect(document.body.style.overflow).toBe('')
  })

  it('restores focus to a re-rendered trigger after the original is removed', async () => {
    const Host = defineComponent({
      setup() {
        const open = ref(false)
        const showTrigger = ref(true)
        const returnFocus = ref<HTMLElement | null>(null)
        return () => h('div', [
          showTrigger.value ? h('button', {
            id: 'trigger',
            onClick: (event: MouseEvent) => {
              returnFocus.value = event.currentTarget as HTMLElement
              showTrigger.value = false
              open.value = true
            },
          }, 'Open modal') : null,
          open.value ? h(ModalDialog, {
            title: 'Example dialog',
            returnFocus: returnFocus.value,
            onClose: () => {
              open.value = false
              showTrigger.value = true
            },
          }, { default: () => h('button', { id: 'inside' }, 'Inside dialog') }) : null,
        ])
      },
    })
    wrapper = mount(Host, { attachTo: document.body })
    const originalTrigger = document.querySelector<HTMLButtonElement>('#trigger')
    if (!originalTrigger) throw new Error('The trigger button was not rendered.')
    originalTrigger.focus()
    await wrapper.get('#trigger').trigger('click')
    await flushPromises()
    expect(originalTrigger.isConnected).toBe(false)

    await wrapper.get('[role="dialog"]').trigger('keydown', { key: 'Escape' })
    await flushPromises()

    const renderedTrigger = wrapper.get('#trigger').element
    expect(renderedTrigger).not.toBe(originalTrigger)
    expect(document.activeElement).toBe(renderedTrigger)
  })
})
