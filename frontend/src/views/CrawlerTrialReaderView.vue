<template>
  <main ref="readerRoot" class="trial-reader" :class="[`theme-${theme}`, { fullscreen: isFullscreen }]">
    <header class="reader-bar">
      <button class="icon-action back-action" type="button" aria-label="返回采集书籍" @click="goBack">‹</button>
      <div class="book-identity">
        <div class="title-line">
          <strong :title="book?.bookName">{{ book?.bookName || '临时试读' }}</strong>
          <span class="trial-badge">临时试读</span>
        </div>
        <p>{{ activeChapter?.chapterName || '正在准备章节' }}<span v-if="readableChapters.length"> · {{ activeIndex + 1 }} / {{ readableChapters.length }}</span></p>
      </div>
      <div class="reader-tools">
        <button class="icon-action" type="button" title="章节目录" aria-label="章节目录" @click="tocOpen=!tocOpen">☰</button>
        <button class="icon-action" type="button" title="阅读设置" aria-label="阅读设置" @click="settingsOpen=!settingsOpen">Aa</button>
        <button class="icon-action" type="button" :title="isFullscreen?'退出全屏':'全屏'" :aria-label="isFullscreen?'退出全屏':'全屏'" @click="toggleFullscreen">{{ isFullscreen?'⊡':'⊞' }}</button>
      </div>
    </header>

    <div class="temporary-notice" role="status"><i aria-hidden="true"/>本次阅读不会保存进度，退出后将从头开始</div>

    <div class="reader-stage">
      <Transition name="panel-slide">
        <aside v-if="tocOpen" class="toc-panel" aria-label="试读章节目录">
          <div class="panel-heading"><div><small>CHAPTER INDEX</small><h2>章节目录</h2></div><button type="button" aria-label="关闭目录" @click="tocOpen=false">×</button></div>
          <label class="toc-search"><span>⌕</span><input v-model="chapterKeyword" type="search" placeholder="查找章节" aria-label="查找章节" /></label>
          <div class="toc-list">
            <button v-for="chapter in filteredChapters" :key="chapter.id" type="button" :class="{active:chapter.id===activeChapter?.id}" @click="selectChapter(chapter)"><span>{{ chapter.chapterIndex + 1 }}</span><strong>{{ chapter.chapterName }}</strong></button>
            <p v-if="!filteredChapters.length" class="empty-list">没有匹配的章节</p>
          </div>
        </aside>
      </Transition>

      <section class="reading-surface">
        <div v-if="loading" class="reader-state" role="status"><span class="spinner"/><p>正在展开书页…</p></div>
        <div v-else-if="loadError" class="reader-state error-state" role="alert"><b>章节暂时无法打开</b><p>{{ loadError }}</p><button type="button" @click="reload">重新加载</button></div>
        <article v-else :style="articleStyle">
          <p class="chapter-kicker">{{ book?.author || '未知作者' }} · {{ book?.siteName }}</p>
          <h1>{{ activeChapter?.chapterName }}</h1>
          <div class="chapter-rule"><span>◆</span></div>
          <p v-for="(paragraph,index) in paragraphs" :key="index" class="body-paragraph">{{ paragraph }}</p>
          <p v-if="!paragraphs.length" class="empty-content">本章暂无可试读正文</p>
        </article>
      </section>

      <Transition name="panel-slide-right">
        <aside v-if="settingsOpen" class="settings-panel" aria-label="临时阅读设置">
          <div class="panel-heading"><div><small>READING ROOM</small><h2>阅读设置</h2></div><button type="button" aria-label="关闭设置" @click="settingsOpen=false">×</button></div>
          <div class="setting-group"><label>阅读主题</label><div class="theme-segment" role="radiogroup" aria-label="阅读主题"><span :style="{transform:`translateX(${themeIndex*100}%)`}"/><button v-for="item in themes" :key="item.value" type="button" role="radio" :aria-checked="theme===item.value" :class="{active:theme===item.value}" @click="theme=item.value">{{ item.label }}</button></div></div>
          <div class="setting-group"><label for="trial-font-size">字号 <b>{{ fontSize }}px</b></label><input id="trial-font-size" v-model="fontSize" type="range" min="14" max="30" /></div>
          <div class="setting-group"><label for="trial-line-height">行距 <b>{{ lineHeight }}</b></label><input id="trial-line-height" v-model="lineHeight" type="range" min="1.4" max="2.5" step="0.1" /></div>
          <div class="setting-group"><label>版心宽度</label><div class="width-options"><button v-for="item in widths" :key="item.value" type="button" :class="{active:contentWidth===item.value}" @click="contentWidth=item.value">{{ item.label }}</button></div></div>
          <p class="settings-note">这些调整也只在当前试读页面生效。</p>
        </aside>
      </Transition>
    </div>

    <footer v-if="readableChapters.length" class="chapter-navigation">
      <button type="button" :disabled="activeIndex<=0||loading" @click="moveChapter(-1)"><span>←</span><small>上一章</small></button>
      <div><span class="progress-track"><i :style="{width:`${temporaryProgress}%`}"/></span><small>临时位置 {{ Math.round(temporaryProgress) }}%</small></div>
      <button type="button" :disabled="activeIndex>=readableChapters.length-1||loading" @click="moveChapter(1)"><small>下一章</small><span>→</span></button>
    </footer>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { crawlerApi, type CrawlerBook, type CrawlerChapter } from '@/utils/crawler'

type ReaderTheme='paper'|'light'|'night'
const route=useRoute(),router=useRouter()
const readerRoot=ref<HTMLElement>()
const book=ref<CrawlerBook>(),readableChapters=ref<CrawlerChapter[]>([]),activeChapter=ref<CrawlerChapter>()
const chapterContent=ref(''),chapterKeyword=ref(''),loading=ref(true),loadError=ref('')
const tocOpen=ref(true),settingsOpen=ref(false),isFullscreen=ref(false)
const theme=ref<ReaderTheme>('paper'),fontSize=ref(19),lineHeight=ref(1.95),contentWidth=ref(760)
const themes:{value:ReaderTheme;label:string}[]=[{value:'paper',label:'宣纸'},{value:'light',label:'明亮'},{value:'night',label:'夜读'}]
const widths=[{value:660,label:'窄'},{value:760,label:'适中'},{value:900,label:'宽'}]
const activeIndex=computed(()=>readableChapters.value.findIndex(item=>item.id===activeChapter.value?.id))
const temporaryProgress=computed(()=>readableChapters.value.length?((activeIndex.value+1)/readableChapters.value.length)*100:0)
const themeIndex=computed(()=>themes.findIndex(item=>item.value===theme.value))
const filteredChapters=computed(()=>{const keyword=chapterKeyword.value.trim().toLocaleLowerCase();return keyword?readableChapters.value.filter(item=>item.chapterName.toLocaleLowerCase().includes(keyword)):readableChapters.value})
const paragraphs=computed(()=>{const text=chapterContent.value.trim();if(!text)return[];const blocks=text.split(/\r?\n\s*\r?\n+/).map(value=>value.trim()).filter(Boolean);return blocks.length>1?blocks:text.split(/\r?\n/).map(value=>value.trim()).filter(Boolean)})
const articleStyle=computed(()=>({'--trial-font-size':`${fontSize.value}px`,'--trial-line-height':String(lineHeight.value),'--trial-content-width':`${contentWidth.value}px`}))

async function loadChapters(bookId:number){
  const first=await crawlerApi.chapters(bookId,{page:0,size:100,sort:'INDEX_ASC'})
  const remaining=await Promise.all(Array.from({length:Math.max(0,first.totalPages-1)},(_,index)=>crawlerApi.chapters(bookId,{page:index+1,size:100,sort:'INDEX_ASC'})))
  readableChapters.value=[...first.content,...remaining.flatMap(page=>page.content)].filter(chapter=>chapter.crawlStatus==='COMPLETED')
}
async function initialize(){
  const bookId=Number(route.params.id)
  if(!Number.isFinite(bookId)){loadError.value='无效的采集书籍编号';loading.value=false;return}
  loading.value=true;loadError.value=''
  try{
    book.value=await crawlerApi.book(bookId)
    await loadChapters(bookId)
    if(!readableChapters.value.length)throw new Error('这本书还没有已采集完成的章节')
    const requestedId=Number(route.query.chapterId)
    await selectChapter(readableChapters.value.find(item=>item.id===requestedId)||readableChapters.value[0],false)
  }catch(error){loadError.value=error instanceof Error?error.message:'试读内容加载失败'}finally{loading.value=false}
}
async function selectChapter(chapter:CrawlerChapter,closeOnMobile=true){
  if(!book.value)return
  loading.value=true;loadError.value=''
  try{
    const detail=await crawlerApi.chapter(book.value.id,chapter.id)
    activeChapter.value=chapter;chapterContent.value=detail.content||''
    await router.replace({query:{...route.query,chapterId:String(chapter.id)}})
    await nextTick();document.querySelector('.reading-surface')?.scrollTo({top:0,behavior:'auto'})
    if(closeOnMobile&&window.matchMedia('(max-width: 760px)').matches)tocOpen.value=false
  }catch(error){loadError.value=error instanceof Error?error.message:'章节正文加载失败'}finally{loading.value=false}
}
function moveChapter(offset:number){const target=readableChapters.value[activeIndex.value+offset];if(target)void selectChapter(target)}
function reload(){activeChapter.value?void selectChapter(activeChapter.value,false):void initialize()}
function goBack(){router.back()}
async function toggleFullscreen(){if(!document.fullscreenElement)await readerRoot.value?.requestFullscreen();else await document.exitFullscreen()}
function syncFullscreen(){isFullscreen.value=document.fullscreenElement===readerRoot.value}
function handleKeydown(event:KeyboardEvent){if(event.target instanceof HTMLInputElement)return;if(event.key==='ArrowLeft')moveChapter(-1);else if(event.key==='ArrowRight')moveChapter(1);else if(event.key==='Escape'){tocOpen.value=false;settingsOpen.value=false}}
onMounted(()=>{void initialize();document.addEventListener('keydown',handleKeydown);document.addEventListener('fullscreenchange',syncFullscreen)})
onBeforeUnmount(()=>{document.removeEventListener('keydown',handleKeydown);document.removeEventListener('fullscreenchange',syncFullscreen)})
</script>

<style scoped>
.trial-reader{--reader-bg:#eee5d2;--surface:rgba(250,246,236,.92);--surface-solid:#faf6ec;--ink:#30291f;--muted:#766c5e;--line:rgba(79,62,39,.16);--accent:#8b4e2d;position:fixed;inset:0;z-index:1000;display:flex;height:100dvh;min-height:560px;overflow:hidden;flex-direction:column;background:radial-gradient(circle at 50% -20%,rgba(255,255,255,.82),transparent 42%),linear-gradient(135deg,rgba(115,83,44,.06) 25%,transparent 25%) 0 0/22px 22px,var(--reader-bg);color:var(--ink)}
.trial-reader.theme-light{--reader-bg:#f4f5f6;--surface:rgba(255,255,255,.94);--surface-solid:#fff;--ink:#202226;--muted:#6c7078;--line:rgba(24,30,40,.12);--accent:#8a4b2b}.trial-reader.theme-night{--reader-bg:#17191c;--surface:rgba(34,37,41,.94);--surface-solid:#222529;--ink:#d8d2c6;--muted:#918b82;--line:rgba(255,255,255,.1);--accent:#d4986d}.trial-reader.fullscreen{height:100dvh}
.reader-bar{z-index:5;display:grid;grid-template-columns:48px minmax(0,1fr) auto;align-items:center;gap:14px;padding:12px 22px;border-bottom:1px solid var(--line);background:var(--surface);backdrop-filter:blur(20px)}.book-identity{min-width:0}.title-line{display:flex;align-items:center;gap:10px}.title-line strong{overflow:hidden;font:600 17px/1.2 'Iowan Old Style','Songti SC',serif;text-overflow:ellipsis;white-space:nowrap}.trial-badge{flex:0 0 auto;padding:3px 8px;border:1px solid color-mix(in srgb,var(--accent) 34%,transparent);border-radius:99px;background:color-mix(in srgb,var(--accent) 10%,transparent);color:var(--accent);font-size:10px;font-weight:800;letter-spacing:.08em}.book-identity p{overflow:hidden;margin:4px 0 0;color:var(--muted);font-size:11px;text-overflow:ellipsis;white-space:nowrap}.reader-tools{display:flex;gap:6px}.icon-action{display:grid;width:38px;height:38px;place-items:center;border:1px solid var(--line);border-radius:11px;background:transparent;color:var(--ink);font-weight:700;cursor:pointer}.icon-action:hover,.icon-action:focus-visible{border-color:var(--accent);color:var(--accent);outline:none}.back-action{font-size:27px;font-weight:400}.temporary-notice{display:flex;z-index:4;align-items:center;justify-content:center;gap:8px;padding:7px 16px;border-bottom:1px solid color-mix(in srgb,var(--accent) 18%,transparent);background:color-mix(in srgb,var(--accent) 8%,var(--surface-solid));color:var(--muted);font-size:11px}.temporary-notice i{width:6px;height:6px;border-radius:50%;background:var(--accent)}
.reader-stage{position:relative;display:flex;min-height:0;flex:1}.reading-surface{flex:1;overflow:auto;scrollbar-color:var(--line) transparent}.reading-surface article{width:min(var(--trial-content-width),calc(100% - 64px));min-height:100%;box-sizing:border-box;margin:0 auto;padding:70px 0 130px}.chapter-kicker{margin:0 0 14px!important;color:var(--accent)!important;font-size:11px!important;font-weight:800;letter-spacing:.12em;text-align:center;text-indent:0!important;text-transform:uppercase}.reading-surface h1{margin:0;text-align:center;font:600 clamp(27px,4vw,39px)/1.35 'Iowan Old Style','Songti SC',serif}.chapter-rule{display:flex;align-items:center;gap:12px;margin:25px auto 38px;color:var(--accent);font-size:8px}.chapter-rule::before,.chapter-rule::after{width:70px;height:1px;background:linear-gradient(90deg,transparent,var(--line));content:''}.chapter-rule::after{background:linear-gradient(90deg,var(--line),transparent)}.body-paragraph{margin:0 0 1.05em;color:var(--ink);font:var(--trial-font-size)/var(--trial-line-height) 'Iowan Old Style','Songti SC','STSong',serif;letter-spacing:.025em;text-align:justify;text-indent:2em;overflow-wrap:anywhere}.empty-content{text-align:center;color:var(--muted)}
.toc-panel,.settings-panel{z-index:3;width:min(330px,88vw);flex:0 0 auto;border-right:1px solid var(--line);background:var(--surface);box-shadow:14px 0 38px rgba(40,30,20,.08);backdrop-filter:blur(22px)}.settings-panel{order:2;border-right:0;border-left:1px solid var(--line);box-shadow:-14px 0 38px rgba(40,30,20,.08);padding-bottom:22px;overflow:auto}.panel-heading{display:flex;align-items:center;justify-content:space-between;padding:22px 20px 14px}.panel-heading small{color:var(--accent);font-size:9px;font-weight:900;letter-spacing:.15em}.panel-heading h2{margin:3px 0 0;font:600 22px 'Iowan Old Style','Songti SC',serif}.panel-heading>button{border:0;background:transparent;color:var(--muted);font-size:24px;cursor:pointer}.toc-search{display:flex;align-items:center;gap:8px;margin:0 16px 12px;padding:9px 12px;border:1px solid var(--line);border-radius:11px;background:color-mix(in srgb,var(--surface-solid) 75%,transparent)}.toc-search input{min-width:0;width:100%;border:0;outline:0;background:transparent;color:var(--ink)}.toc-list{height:calc(100% - 116px);overflow:auto;padding:0 10px 18px}.toc-list button{display:grid;width:100%;grid-template-columns:34px minmax(0,1fr);align-items:center;gap:7px;padding:10px;border:0;border-radius:10px;background:transparent;color:var(--muted);text-align:left;cursor:pointer}.toc-list button span{font:11px ui-monospace,monospace;text-align:center}.toc-list button strong{overflow:hidden;font-size:12px;font-weight:500;text-overflow:ellipsis;white-space:nowrap}.toc-list button:hover,.toc-list button.active{background:color-mix(in srgb,var(--accent) 10%,transparent);color:var(--accent)}.empty-list{padding:30px 12px;text-align:center;color:var(--muted);font-size:12px}
.setting-group{display:grid;gap:10px;padding:16px 20px;border-top:1px solid var(--line)}.setting-group>label{display:flex;justify-content:space-between;color:var(--muted);font-size:12px}.setting-group b{color:var(--ink)}.setting-group input[type=range]{width:100%;accent-color:var(--accent)}.theme-segment{position:relative;display:grid;grid-template-columns:repeat(3,1fr);padding:3px;border:1px solid var(--line);border-radius:11px;background:color-mix(in srgb,var(--reader-bg) 72%,transparent);isolation:isolate}.theme-segment span{position:absolute;inset:3px auto 3px 3px;z-index:-1;width:calc((100% - 6px)/3);border-radius:8px;background:var(--surface-solid);box-shadow:0 2px 8px rgba(0,0,0,.08);transition:transform .22s ease}.theme-segment button{padding:8px 4px;border:0;background:transparent;color:var(--muted);font-size:11px;cursor:pointer}.theme-segment button.active{color:var(--accent);font-weight:800}.width-options{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}.width-options button{padding:8px;border:1px solid var(--line);border-radius:9px;background:transparent;color:var(--muted);cursor:pointer}.width-options button.active{border-color:var(--accent);background:color-mix(in srgb,var(--accent) 9%,transparent);color:var(--accent)}.settings-note{margin:14px 20px;color:var(--muted);font-size:11px;line-height:1.6}
.chapter-navigation{position:absolute;right:24px;bottom:18px;left:24px;z-index:6;display:grid;grid-template-columns:120px minmax(120px,260px) 120px;align-items:center;justify-content:center;gap:22px;width:min(650px,calc(100% - 48px));box-sizing:border-box;margin:auto;padding:9px 12px;border:1px solid var(--line);border-radius:16px;background:var(--surface);box-shadow:0 14px 40px rgba(30,24,18,.14);backdrop-filter:blur(20px)}.chapter-navigation button{display:flex;align-items:center;justify-content:center;gap:8px;padding:7px;border:0;background:transparent;color:var(--ink);cursor:pointer}.chapter-navigation button:disabled{opacity:.28;cursor:not-allowed}.chapter-navigation button span{font-size:18px}.chapter-navigation button small,.chapter-navigation>div small{color:var(--muted);font-size:10px}.chapter-navigation>div{display:grid;gap:5px;text-align:center}.progress-track{height:3px;overflow:hidden;border-radius:99px;background:var(--line)}.progress-track i{display:block;height:100%;border-radius:inherit;background:var(--accent);transition:width .25s ease}.reader-state{display:grid;height:100%;place-content:center;place-items:center;gap:12px;color:var(--muted)}.spinner{width:28px;height:28px;border:2px solid var(--line);border-top-color:var(--accent);border-radius:50%;animation:spin .8s linear infinite}.error-state b{color:var(--ink)}.error-state p{margin:0}.error-state button{padding:8px 16px;border:1px solid var(--accent);border-radius:9px;background:transparent;color:var(--accent);cursor:pointer}@keyframes spin{to{transform:rotate(360deg)}}
.panel-slide-enter-active,.panel-slide-leave-active,.panel-slide-right-enter-active,.panel-slide-right-leave-active{transition:transform .24s ease,opacity .2s ease}.panel-slide-enter-from,.panel-slide-leave-to{transform:translateX(-100%);opacity:0}.panel-slide-right-enter-from,.panel-slide-right-leave-to{transform:translateX(100%);opacity:0}
@media(max-width:760px){.reader-bar{grid-template-columns:38px minmax(0,1fr) auto;padding:9px 10px;gap:8px}.reader-tools{gap:3px}.icon-action{width:34px;height:34px}.reader-tools .icon-action:last-child{display:none}.trial-badge{display:none}.temporary-notice{justify-content:flex-start;padding-left:14px}.toc-panel,.settings-panel{position:absolute;inset:0 auto 0 0;height:100%;box-sizing:border-box}.settings-panel{right:0;left:auto}.reading-surface article{width:calc(100% - 34px);padding:48px 0 120px}.chapter-navigation{right:10px;bottom:10px;left:10px;width:calc(100% - 20px);grid-template-columns:82px minmax(90px,1fr) 82px;gap:6px}.chapter-navigation button small{display:none}}
@media(prefers-reduced-motion:reduce){.theme-segment span,.progress-track i,.panel-slide-enter-active,.panel-slide-leave-active,.panel-slide-right-enter-active,.panel-slide-right-leave-active{transition:none}.spinner{animation:none}}
</style>
