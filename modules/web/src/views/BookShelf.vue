<template>
  <div :class="{ 'index-wrapper': true, night: isNight, day: !isNight }">
    <div class="navigation-wrapper">
      <div class="navigation-title-wrapper">
        <div class="navigation-title">阅读</div>
        <div class="navigation-sub-title">清风不识字，何故乱翻书</div>
      </div>
      <div class="search-wrapper">
        <el-input
          placeholder="搜索本地书架，按 Enter 在线搜索"
          v-model="searchWord"
          class="search-input"
          :prefix-icon="SearchIcon"
          @keyup.enter="searchBook"
        >
        </el-input>
      </div>
      <div class="bottom-wrapper">
        <div class="recent-wrapper">
          <div class="recent-title-row">
            <div class="recent-title">阅读历史</div>
            <el-button
              v-if="readingHistoryItems.length > 0"
              class="history-clear"
              link
              size="small"
              type="danger"
              :icon="DeleteIcon"
              @click="clearReadingHistory"
            >
              清空
            </el-button>
          </div>
          <div class="reading-history">
            <a
              v-if="readingHistory.length === 0 && readingHistoryItems.length > 0"
              :key="readingRecent.bookUrl"
              class="history-item"
              :href="getChapterHref(readingRecent)"
              @click="handleHistoryClick($event, readingHistoryItems[0])"
            >
              <span class="history-main">
                <span class="history-name">{{ readingHistoryItems[0].name }}</span>
                <span class="history-chapter">{{
                  readingHistoryItems[0].chapterTitle
                }}</span>
              </span>
              <el-button
                class="history-delete"
                text
                circle
                size="small"
                type="danger"
                :icon="CloseBoldIcon"
                :aria-label="`删除${readingHistoryItems[0].name}的阅读历史`"
                @click.stop.prevent="deleteReadingHistory(readingHistoryItems[0])"
              />
            </a>
            <template v-else>
              <a
                v-for="item in readingHistoryItems"
                :key="item.bookUrl"
                class="history-item"
                :href="getChapterHref(item)"
                @click="handleHistoryClick($event, item)"
              >
                <span class="history-main">
                  <span class="history-name">{{ item.name }}</span>
                  <span class="history-chapter">{{ item.chapterTitle }}</span>
                </span>
                <el-button
                  class="history-delete"
                  text
                  circle
                  size="small"
                  type="danger"
                  :icon="CloseBoldIcon"
                  :aria-label="`删除${item.name}的阅读历史`"
                  @click.stop.prevent="deleteReadingHistory(item)"
                />
              </a>
            </template>
            <el-tag
              v-if="readingHistoryItems.length === 0"
              type="warning"
              class="recent-book"
              size="large"
            >
              尚无阅读记录
            </el-tag>
          </div>
        </div>
        <div class="setting-wrapper">
          <div class="setting-title">基本设定</div>
          <div class="setting-item">
            <el-tag
              :type="connectType"
              size="large"
              class="setting-connect"
              :class="{ 'no-point': newConnect }"
              @click="setLegadoRetmoteUrl"
            >
              {{ connectStatus }}
            </el-tag>
          </div>
        </div>
      </div>
      <div class="bottom-icons">
        <a
          href="https://github.com/gedoor/legado_web_bookshelf"
          target="_blank"
        >
          <div class="bottom-icon">
            <img :src="githubUrl" alt="" />
          </div>
        </a>
      </div>
    </div>
    <div class="shelf-wrapper" ref="shelfWrapper">
      <book-items
        v-if="isOnlineSearching"
        :books="onlineBooks"
        @bookClick="handleBookClick"
        :isSearch="true"
      ></book-items>
      <div v-else class="grouped-shelf">
        <div class="shelf-summary">
          <span v-if="localSearchActive">本地搜索：{{ localResultCount }} 本</span>
          <span v-else>本地书架：{{ shelf.length }} 本</span>
        </div>
        <div v-if="groupedLocalBooks.length === 0" class="shelf-empty">
          没有符合的本地书籍
        </div>
        <section
          v-for="category in groupedLocalBooks"
          :key="category.kind"
          class="category-section"
        >
          <div class="category-header">
            <h2>{{ category.kind }}</h2>
            <span>{{ category.count }} 本</span>
          </div>
          <section
            v-for="authorGroup in category.authorGroups"
            :key="`${category.kind}:${authorGroup.author}`"
            class="author-section"
          >
            <div class="author-header">
              <h3>{{ authorGroup.author }}</h3>
              <span>{{ authorGroup.count }} 本</span>
            </div>
            <book-items
              :books="authorGroup.books"
              @bookClick="handleBookClick"
              :isSearch="false"
              :embedded="true"
            ></book-items>
          </section>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import '@/assets/bookshelf.css'
import '@/assets/fonts/shelffont.css'
import { useBookStore } from '@/store'
import githubUrl from '@/assets/imgs/github.png'
import { useLoading } from '@/hooks/loading'
import {
  CloseBold as CloseBoldIcon,
  Delete as DeleteIcon,
  Search as SearchIcon,
} from '@element-plus/icons-vue'
import { baseURL_localStorage_key } from '@/api/axios'
import API, {
  legado_http_entry_point,
  parseLeagdoHttpUrlWithDefault,
  setApiEntryPoint,
} from '@api'
import {
  getLocalStorageItem,
  removeLocalStorageItem,
  setLocalStorageItem,
  setSessionStorageItem,
} from '@/utils/browserStorage'
import { getChapterHref, getChapterQuery } from '@/utils/chapterLink'
import {
  filterBookshelfBooks,
  groupBookshelfBooks,
  normalizeBookshelfSearch,
} from '@/utils/bookshelfGrouping'
import { validatorHttpUrl } from '@/utils/utils'
import type { Book, SeachBook } from '@/book'
import type { webReadConfig } from '@/web'

type ReadingHistoryItem = {
  name: string
  author: string
  bookUrl: string
  chapterIndex: number
  chapterPos: number
  chapterTitle: string
  isSeachBook?: boolean
  fromLocalRecent: boolean
}

const store = useBookStore()
const route = useRoute()
const isNight = computed(() => store.isNight)

/** shortcuts of `store.setConfig` */
const applyReadConfig = (config?: webReadConfig) => {
  try {
    if (config !== undefined) store.setConfig(config)
  } catch {
    ElMessage.info('阅读界面配置解析错误')
  }
}

const readingRecent = ref<typeof store.readingBook>({
  name: '尚无阅读记录',
  author: '',
  bookUrl: '',
  chapterIndex: 0,
  chapterPos: 0,
  isSeachBook: false,
})
const readingHistory = ref<Book[]>([])

const toHistoryItem = (book: Book): ReadingHistoryItem => ({
  name: book.name,
  author: book.author,
  bookUrl: book.bookUrl,
  chapterIndex: book.durChapterIndex ?? 0,
  chapterPos: book.durChapterPos ?? 0,
  chapterTitle:
    book.durChapterTitle || `第${(book.durChapterIndex ?? 0) + 1}章`,
  isSeachBook: false,
  fromLocalRecent: false,
})

const toRecentHistoryItem = (): ReadingHistoryItem | undefined => {
  if (readingRecent.value.bookUrl === '') return undefined
  return {
    name: readingRecent.value.name,
    author: readingRecent.value.author,
    bookUrl: readingRecent.value.bookUrl,
    chapterIndex: readingRecent.value.chapterIndex,
    chapterPos: readingRecent.value.chapterPos,
    chapterTitle: '本机记录',
    isSeachBook: readingRecent.value.isSeachBook,
    fromLocalRecent: true,
  }
}

const readingHistoryItems = computed<ReadingHistoryItem[]>(() => {
  if (readingHistory.value.length > 0) {
    return readingHistory.value.map(toHistoryItem)
  }
  const recentItem = toRecentHistoryItem()
  return recentItem === undefined ? [] : [recentItem]
})

const shelfWrapper = ref<HTMLElement>()
//const shelfWrapper = useTemplateRef<HTMLElement>("shelfWrapper")
const { showLoading, closeLoading, loadingWrapper, isLoading } = useLoading(
  shelfWrapper,
  '正在获取书籍信息',
)

// 书架书籍和在线书籍搜索
const onlineBooks = shallowRef<SeachBook[]>([])
const shelf = computed(() => store.shelf)
const searchWord = ref('')
const isOnlineSearching = ref(false)
let suppressSearchWordReset = false
const localBooks = computed(() => filterBookshelfBooks(shelf.value, searchWord.value))
const groupedLocalBooks = computed(() => groupBookshelfBooks(localBooks.value))
const localSearchActive = computed(
  () => normalizeBookshelfSearch(searchWord.value) !== '',
)
const localResultCount = computed(() => localBooks.value.length)

watch(searchWord, () => {
  if (suppressSearchWordReset) {
    suppressSearchWordReset = false
    return
  }
  isOnlineSearching.value = false
  onlineBooks.value = []
})
//搜索在线书籍
const searchBook = () => {
  if (searchWord.value == '') return
  onlineBooks.value = []
  store.clearSearchBooks()
  showLoading()
  isOnlineSearching.value = true
  API.search(
    searchWord.value,
    searcBooks => {
      if (isLoading) {
        closeLoading()
      }
      try {
        store.setSearchBooks(searcBooks)
        onlineBooks.value = store.searchBooks
        //store.searchBooks.forEach((item) => books.value.push(item));
      } catch (e) {
        ElMessage.error('后端数据错误')
        throw e
      }
    },
    () => {
      closeLoading()
      if (onlineBooks.value.length == 0) {
        ElMessage.info('搜索结果为空')
      }
    },
  )
}

//连接状态
const connectionStore = useConnectionStore()
const { connectStatus, connectType, newConnect } = storeToRefs(connectionStore)

const setLegadoRetmoteUrl = () => {
  ElMessageBox.prompt(
    '请输入 后端地址 ( 如：http://127.0.0.1:9527 或者通过内网穿透的地址)',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: legado_http_entry_point,
      inputValidator: value => validatorHttpUrl(value),
      inputErrorMessage: '输入的格式不对',
      beforeClose: (action, instance, done) => {
        if (action === 'confirm') {
          connectionStore.setNewConnect(true)
          instance.confirmButtonLoading = true
          instance.confirmButtonText = '校验中……'
          // instance.inputValue
          const url = new URL(instance.inputValue).toString()
          API.getReadConfig(url)
            .then(function (config) {
              connectionStore.setNewConnect(false)
              applyReadConfig(config)
              instance.confirmButtonLoading = false
              store.clearSearchBooks()
              setApiEntryPoint(...parseLeagdoHttpUrlWithDefault(url))
              if (url === location.origin) {
                removeLocalStorageItem(baseURL_localStorage_key)
              } else {
                setLocalStorageItem(baseURL_localStorage_key, url)
              }
              store.loadBookShelf()
              void loadReadingHistory()
              done()
            })
            .catch(function (error) {
              connectionStore.setNewConnect(false)
              instance.confirmButtonLoading = false
              instance.confirmButtonText = '确定'
              throw error
            })
        } else {
          done()
        }
      },
    },
  )
}

const router = useRouter()
const handleBookClick = async (book: SeachBook | Book) => {
  // 判断是否为 searchBook
  const isSeachBook = 'respondTime' in book
  if (isSeachBook) {
    await API.saveBook(book)
  }
  const {
    bookUrl,
    name,
    author,
    // @ts-expect-error: descruct with default value
    durChapterIndex = 0,
    // @ts-expect-error: descruct with default value
    durChapterPos = 0,
  } = book

  toDetail(bookUrl, name, author, durChapterIndex, durChapterPos, isSeachBook)
}
const toDetail = (
  bookUrl: string,
  bookName: string,
  bookAuthor: string,
  chapterIndex: number,
  chapterPos: number,
  isSeachBook: boolean | undefined = false,
  fromReadRecentClick = false,
) => {
  if (bookName === '尚无阅读记录') return
  // 最近书籍不再书架上 自动搜索
  if (
    fromReadRecentClick &&
    shelf.value.every(book => book.bookUrl !== bookUrl)
  ) {
    suppressSearchWordReset = true
    searchWord.value = bookName
    searchBook()
    void nextTick(() => {
      suppressSearchWordReset = false
    })
    return
  }
  const nextReadingBook = {
    name: bookName,
    author: bookAuthor,
    bookUrl,
    chapterIndex,
    chapterPos,
    isSeachBook,
  }
  store.setReadingBook(nextReadingBook)
  setSessionStorageItem('bookUrl', bookUrl)
  setSessionStorageItem('bookName', bookName)
  setSessionStorageItem('bookAuthor', bookAuthor)
  setSessionStorageItem('chapterIndex', String(chapterIndex))
  setSessionStorageItem('chapterPos', String(chapterPos))
  setSessionStorageItem('isSeachBook', String(isSeachBook))
  readingRecent.value = nextReadingBook
  setLocalStorageItem('readingRecent', JSON.stringify(nextReadingBook))
  router.push({
    path: '/chapter',
    query: getChapterQuery(nextReadingBook, route.query),
  })
}

const openHistoryItem = (item: ReadingHistoryItem, event?: MouseEvent) => {
  if (item.bookUrl === '') {
    event?.preventDefault()
    return
  }
  toDetail(
    item.bookUrl,
    item.name,
    item.author,
    item.chapterIndex,
    item.chapterPos,
    item.isSeachBook,
    item.fromLocalRecent,
  )
}

const handleHistoryClick = (event: MouseEvent, item: ReadingHistoryItem) => {
  openHistoryItem(item, event)
}

const removeReadingRecentIfMatches = (bookUrl: string) => {
  if (readingRecent.value.bookUrl !== bookUrl) return
  readingRecent.value = {
    name: '尚无阅读记录',
    author: '',
    bookUrl: '',
    chapterIndex: 0,
    chapterPos: 0,
    isSeachBook: false,
  }
  removeLocalStorageItem('readingRecent')
}

const loadReadingHistory = async () => {
  try {
    const resp = await API.getReadingHistory()
    const { isSuccess, data, errorMsg } = resp.data
    if (isSuccess === true) {
      readingHistory.value = data
      return
    }
    readingHistory.value = []
    ElMessage.error(errorMsg || '阅读历史加载失败')
  } catch {
    readingHistory.value = []
  }
}

const deleteReadingHistory = async (item: ReadingHistoryItem) => {
  try {
    const resp = await API.deleteReadingHistory({ bookUrl: item.bookUrl })
    const { isSuccess, errorMsg } = resp.data
    if (isSuccess !== true) {
      ElMessage.error(errorMsg || '阅读历史删除失败')
      return
    }
    readingHistory.value = readingHistory.value.filter(
      book => book.bookUrl !== item.bookUrl,
    )
    removeReadingRecentIfMatches(item.bookUrl)
  } catch {
    ElMessage.error('阅读历史删除失败')
  }
}

const clearReadingHistory = async () => {
  try {
    await ElMessageBox.confirm('确定清空全部阅读历史？', '清空阅读历史', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    const resp = await API.clearReadingHistory()
    const { isSuccess, errorMsg } = resp.data
    if (isSuccess !== true) {
      ElMessage.error(errorMsg || '阅读历史清空失败')
      return
    }
    readingHistory.value = []
    readingRecent.value = {
      name: '尚无阅读记录',
      author: '',
      bookUrl: '',
      chapterIndex: 0,
      chapterPos: 0,
      isSeachBook: false,
    }
    removeLocalStorageItem('readingRecent')
  } catch {
    ElMessage.error('阅读历史清空失败')
  }
}

const loadShelf = async () => {
  await store.loadWebConfig()
  await store.saveBookProgress({ beacon: false })
  //确保各种网络情况下同步请求先完成
  await store.loadBookShelf()
  await loadReadingHistory()
}

onMounted(() => {
  //获取最近阅读书籍
  const readingRecentStr = getLocalStorageItem('readingRecent')
  if (readingRecentStr != null) {
    try {
      readingRecent.value = JSON.parse(readingRecentStr)
      if (typeof readingRecent.value.chapterIndex == 'undefined') {
        readingRecent.value.chapterIndex = 0
      }
    } catch {
      removeLocalStorageItem('readingRecent')
    }
  }
  console.log('bookshelf mounted')
  loadingWrapper(loadShelf())
})
</script>

<style lang="scss" scoped>
.index-wrapper {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: row;

  .navigation-wrapper {
    width: 260px;
    min-width: 260px;
    padding: 48px 36px;
    background-color: #f7f7f7;

    .navigation-title {
      font-size: 24px;
      font-weight: 500;
      font-family: FZZCYSK;
    }

    .navigation-sub-title {
      font-size: 16px;
      font-weight: 300;
      font-family: FZZCYSK;
      margin-top: 16px;
      color: #b1b1b1;
    }

    .search-wrapper {
      .search-input {
        border-radius: 50%;
        margin-top: 24px;

        :deep(.el-input__wrapper) {
          border-radius: 50px;
          border-color: #e3e3e3;
        }
      }
    }

    .bottom-wrapper {
      display: flex;
      flex-direction: column;
    }

    .recent-wrapper {
      margin-top: 36px;

      .recent-title-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
      }

      .recent-title {
        font-size: 14px;
        color: #b1b1b1;
        font-family: FZZCYSK;
      }

      .history-clear {
        min-width: 42px;
        padding: 0;
      }

      .reading-history {
        margin: 16px 0 0;
        display: flex;
        flex-direction: column;
        gap: 8px;

        .history-item {
          color: inherit;
          text-decoration: none;
          display: grid;
          grid-template-columns: minmax(0, 1fr) 28px;
          align-items: center;
          gap: 6px;
          min-height: 34px;
          padding: 2px 0;
        }

        .history-main {
          display: flex;
          min-width: 0;
          flex-direction: column;
          gap: 2px;
        }

        .history-name,
        .history-chapter {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .history-name {
          color: #33373d;
          font-size: 12px;
          font-weight: 600;
        }

        .history-chapter {
          color: #8c8c8c;
          font-size: 10px;
        }

        .history-delete {
          width: 28px;
          height: 28px;
        }

        .recent-book {
          width: fit-content;
          max-width: 100%;
          font-size: 10px;
          cursor: default;
        }
      }
    }

    .setting-wrapper {
      margin-top: 36px;

      .setting-title {
        font-size: 14px;
        color: #b1b1b1;
        font-family: FZZCYSK;
      }

      .no-point {
        pointer-events: none;
      }

      .setting-connect {
        font-size: 8px;
        margin-top: 16px;
        /*         // color: #6B7C87; */
        cursor: pointer;
      }
    }

    .bottom-icons {
      position: fixed;
      bottom: 0;
      height: 120px;
      width: 260px;
      align-items: center;
      display: flex;
      flex-direction: row;
    }
  }

  .shelf-wrapper {
    padding: 48px 48px;
    width: 100%;
    display: flex;
    flex-direction: column;
    box-sizing: border-box;
    overflow: hidden;

    .grouped-shelf {
      height: 100%;
      min-height: 0;
      overflow: auto;
      -webkit-overflow-scrolling: touch;
    }

    .shelf-summary {
      color: #8c8c8c;
      font-size: 13px;
      font-weight: 600;
      margin-bottom: 16px;
    }

    .shelf-empty {
      color: #969ba3;
      font-size: 14px;
      padding: 24px 0;
    }

    .category-section {
      margin-bottom: 28px;
    }

    .category-header,
    .author-header {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 12px;
    }

    .category-header {
      border-bottom: 1px solid rgba(0, 0, 0, 0.08);
      margin-bottom: 14px;
      padding-bottom: 8px;

      h2 {
        color: #33373d;
        font-size: 20px;
        font-weight: 700;
        margin: 0;
      }

      span {
        color: #8c8c8c;
        font-size: 12px;
      }
    }

    .author-section {
      margin-bottom: 18px;
    }

    .author-header {
      margin: 0 0 8px;

      h3 {
        color: #555b63;
        font-size: 14px;
        font-weight: 700;
        margin: 0;
      }

      span {
        color: #a0a0a0;
        font-size: 11px;
      }
    }
  }
}

@media screen and (max-width: 750px) {
  .index-wrapper {
    overflow-x: hidden;
    flex-direction: column;

    .navigation-wrapper {
      padding: 20px 24px;
      box-sizing: border-box;
      width: 100%;

      .navigation-title-wrapper {
        white-space: nowrap;
        display: flex;
        justify-content: space-between;
        align-items: flex-end;
      }

      .bottom-wrapper {
        flex-direction: row;

        > * {
          flex-grow: 1;
          margin-top: 18px;

          .reading-recent,
          .setting-item {
            margin-bottom: 0px;
          }
        }
      }

      .bottom-icons {
        display: none;
      }
    }

    .shelf-wrapper {
      padding: 0;
      flex-grow: 1;
      min-height: 0;
      overflow: auto;
      -webkit-overflow-scrolling: touch;

      .grouped-shelf {
        padding: 0 0 20px;
        overflow: visible;
        -webkit-overflow-scrolling: auto;
      }

      .shelf-summary,
      .shelf-empty,
      .category-header,
      .author-header {
        padding-left: 20px;
        padding-right: 20px;
      }

      .category-header {
        margin-top: 14px;

        h2 {
          font-size: 17px;
        }
      }

      :deep(.el-loading-spinner) {
        display: none;
      }
    }
  }
}

.night {
  .navigation-wrapper {
    background-color: #454545;

    .navigation-title {
      color: #aeaeae;
    }

    .search-wrapper {
      .search-input {
        .el-input__wrapper {
          background-color: #454545;
        }

        .el-input__inner {
          color: #b1b1b1;
        }
      }
    }

    .reading-history {
      .history-name {
        color: #d0d0d0;
      }

      .history-chapter {
        color: #a0a0a0;
      }
    }
  }

  :deep(.shelf-wrapper) {
    background-color: #161819;
  }

  .shelf-wrapper {
    .category-header {
      border-bottom-color: rgba(255, 255, 255, 0.12);

      h2 {
        color: #d0d0d0;
      }
    }

    .author-header {
      h3 {
        color: #c0c0c0;
      }
    }
  }
}
</style>
