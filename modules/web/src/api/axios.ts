import axios from 'axios'
import { getLocalStorageItem } from '@/utils/browserStorage'

/** @type {string} localStorage保存自定义阅读http服务接口的键值 */
export const baseURL_localStorage_key = 'remoteUrl'
const SECOND = 1000

const ajax = axios.create({
  baseURL:
    import.meta.env.VITE_API ||
    getLocalStorageItem(baseURL_localStorage_key) ||
    location.origin,
  timeout: 120 * SECOND,
})

export default ajax
