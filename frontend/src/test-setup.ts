import { config } from '@vue/test-utils'
import { i18n } from './i18n'

config.global.mocks.$t = (...args: Parameters<typeof i18n.global.t>) => i18n.global.t(...args)
