package com.lovelyreader.source

import com.lovelyreader.domain.BookStatus
import com.lovelyreader.domain.RankingPeriod
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SizeBand
import com.lovelyreader.domain.SourceCapability
import kotlin.math.abs

data class PresetNovelSource(
    val sourceId: String,
    val displayName: String,
    val baseUrl: String,
    val userProvided: Boolean = false,
    val supportsSearch: Boolean = true,
    val supportsFreeRead: Boolean = true,
    val supportsDownloadHint: Boolean = false,
    val statusNote: String = ""
)

data class CatalogNovel(
    val title: String,
    val author: String,
    val category: String,
    val status: BookStatus,
    val sizeKb: Int,
    val sourceIds: List<String>,
    val monthScore: Int,
    val yearScore: Int,
    val totalScore: Int,
    val summary: String
)

object AggregatedNovelCatalog {
    val sizeBands = listOf(
        SizeBand("0.5-1M", 512, 1024),
        SizeBand("1-1.5M", 1025, 1536),
        SizeBand("1.5M以上", 1537, 99999)
    )

    val presetSources = listOf(
        PresetNovelSource("ixdzs", "爱下电子书", "https://ixdzs8.com", userProvided = true, supportsDownloadHint = false, statusNote = "已验证章节阅读"),
        PresetNovelSource("yqxz", "言情小说站", "https://yqxz.cc", userProvided = true, supportsSearch = false, supportsFreeRead = false, supportsDownloadHint = false, statusNote = "Cloudflare 拦截，预置但暂不计入已支持"),
        PresetNovelSource("ijjxs", "久久小说下载网", "https://m.ijjxs.com", userProvided = true, supportsFreeRead = false, supportsDownloadHint = false, statusNote = "已做搜索适配，详情与下载继续验证"),
        PresetNovelSource("qisuwang", "奇速小说", "https://m.qisuwang.cc", userProvided = true, supportsDownloadHint = true, statusNote = "已验证TXT下载阅读"),
        PresetNovelSource("zxcs", "知轩藏书", "https://zxcs.zip", userProvided = true, supportsDownloadHint = true, statusNote = "已验证TXT下载阅读"),
        PresetNovelSource("qinkan", "勤看小说", "https://www.qinkan.net", userProvided = true, supportsDownloadHint = true, statusNote = "已验证多格式下载阅读")
    )

    val categories = listOf("现言甜宠", "都市职场", "青春校园", "古言宫斗", "穿越重生", "年代种田", "仙侠奇缘", "悬疑推理")

    private val novels = listOf(
        novel("你是我的荣耀", "顾漫", "都市职场", 760, listOf("ixdzs", "ijjxs"), 98, 99, 100, "久别重逢与成年人的双向奔赴。"),
        novel("何以笙箫默", "顾漫", "现言甜宠", 620, listOf("ixdzs", "qinkan"), 88, 96, 99, "年少时的喜欢，绕了一圈还是回到彼此身边。"),
        novel("微微一笑很倾城", "顾漫", "青春校园", 680, listOf("qisuwang", "ijjxs"), 89, 94, 98, "校园、游戏与轻甜恋爱。"),
        novel("杉杉来吃", "顾漫", "现言甜宠", 540, listOf("yqxz", "qisuwang"), 84, 91, 95, "轻松甜宠，小小日常也能发光。"),
        novel("骄阳似我", "顾漫", "青春校园", 580, listOf("ixdzs", "qinkan"), 82, 88, 93, "明亮、青春、带一点遗憾的成长故事。"),
        novel("偷偷藏不住", "竹已", "现言甜宠", 820, listOf("qinkan", "qisuwang"), 100, 100, 97, "从暗恋到被认真珍惜的甜暖故事。"),
        novel("难哄", "竹已", "都市职场", 900, listOf("ijjxs", "zxcs"), 97, 99, 96, "破镜重圆，温柔和克制都藏在细节里。"),
        novel("白日梦我", "栖见", "青春校园", 880, listOf("qisuwang", "qinkan"), 94, 97, 94, "校园成长与彼此治愈。"),
        novel("她的小梨涡", "唧唧的猫", "青春校园", 710, listOf("yqxz", "ijjxs"), 91, 95, 92, "酸甜校园，明媚又心软。"),
        novel("告白", "应橙", "青春校园", 760, listOf("qinkan", "ixdzs"), 90, 94, 90, "青春里的心动与认真告白。"),
        novel("酸梅", "黄三", "青春校园", 650, listOf("qinkan", "qisuwang"), 86, 91, 89, "夏天、暗恋和成长里的刺痛。"),
        novel("暗格里的秘密", "耳东兔子", "青春校园", 830, listOf("qisuwang", "zxcs"), 93, 96, 91, "从校园到远方，心动藏在暗格里。"),
        novel("他知道风从哪个方向来", "玖月晞", "都市职场", 980, listOf("zxcs", "ixdzs"), 87, 92, 93, "旷野、冒险与坚定的爱情。"),
        novel("亲爱的阿基米德", "玖月晞", "悬疑推理", 1020, listOf("zxcs", "qisuwang"), 82, 89, 91, "推理线与感情线并行。"),
        novel("亲爱的弗洛伊德", "玖月晞", "悬疑推理", 1120, listOf("zxcs", "qinkan"), 81, 88, 90, "悬疑、心理与深情守护。"),
        novel("东宫", "匪我思存", "古言宫斗", 790, listOf("ixdzs", "qinkan"), 85, 93, 94, "爱恨与命运交织的古言名篇。"),
        novel("寂寞空庭春欲晚", "匪我思存", "古言宫斗", 730, listOf("yqxz", "ijjxs"), 80, 87, 92, "宫廷旧梦与深情遗憾。"),
        novel("知否知否应是绿肥红瘦", "关心则乱", "古言宫斗", 1800, listOf("zxcs", "qinkan"), 92, 98, 99, "宅斗、成长与细水长流的生活感。"),
        novel("庶女攻略", "吱吱", "古言宫斗", 1650, listOf("zxcs", "qisuwang"), 78, 86, 92, "稳扎稳打的古言宅斗。"),
        novel("花千骨", "Fresh果果", "仙侠奇缘", 1250, listOf("qisuwang", "ijjxs"), 83, 90, 95, "仙侠虐恋与宿命选择。"),
        novel("三生三世十里桃花", "唐七", "仙侠奇缘", 690, listOf("yqxz", "qinkan"), 84, 89, 94, "仙侠世界里的情深与重逢。"),
        novel("香蜜沉沉烬如霜", "电线", "仙侠奇缘", 890, listOf("qisuwang", "zxcs"), 86, 91, 95, "仙侠、误会与命定情缘。"),
        novel("七零年代之省城媳妇", "末笙", "年代种田", 636, listOf("ijjxs", "ixdzs"), 91, 92, 84, "穿书年代，烟火气里的小日子。"),
        novel("穿成七零娇娇女", "似伊", "年代种田", 980, listOf("qisuwang", "qinkan"), 88, 90, 82, "年代、家长里短和踏实生活。"),
        novel("重生小地主", "弱颜", "穿越重生", 1800, listOf("zxcs", "qinkan"), 74, 84, 90, "重生种田，慢慢把日子过好。"),
        novel("第一侯", "希行", "穿越重生", 1450, listOf("zxcs", "qinkan"), 76, 85, 89, "谋略、成长与女性主角的锋芒。"),
        novel("君九龄", "希行", "穿越重生", 1500, listOf("zxcs", "ixdzs"), 77, 86, 90, "复仇、医术与命运翻盘。"),
        novel("簪中录", "侧侧轻寒", "悬疑推理", 980, listOf("qinkan", "qisuwang"), 82, 88, 91, "古言推理，案件与情感相扣。"),
        novel("长安十二时辰", "马伯庸", "悬疑推理", 1040, listOf("zxcs", "qisuwang"), 73, 83, 88, "节奏紧张的古代悬疑。"),
        novel("惜花芷", "空留", "古言宫斗", 1300, listOf("yqxz", "qinkan"), 90, 94, 88, "家族困境里长出的女性力量。")
    )

    fun search(query: String, seenTitles: Set<String> = emptySet()): List<SearchResult> {
        val normalized = query.normalizeForMatch()
        if (normalized.isBlank()) return emptyList()
        return novels
            .filter { novel ->
                novel.title.normalizeForMatch().contains(normalized) ||
                    novel.author.normalizeForMatch().contains(normalized) ||
                    normalized.contains(novel.title.normalizeForMatch())
            }
            .sortedWith(compareBy<CatalogNovel> { it.title in seenTitles }.thenByDescending { it.totalScore })
            .map { it.toSearchResult() }
    }

    fun ranking(period: RankingPeriod, seenTitles: Set<String> = emptySet()): List<SearchResult> {
        return novels
            .sortedWith(compareBy<CatalogNovel> { it.title in seenTitles }.thenByDescending { it.score(period) })
            .take(20)
            .map { it.toSearchResult(period) }
    }

    fun randomBrowse(
        category: String,
        finishedOnly: Boolean = true,
        sizeBand: SizeBand = sizeBands.first(),
        seenTitles: Set<String> = emptySet()
    ): List<SearchResult> {
        val pool = novels.filter { novel ->
            novel.category == category &&
                (!finishedOnly || novel.status == BookStatus.FINISHED) &&
                sizeBand.contains(novel.sizeKb) &&
                novel.title !in seenTitles
        }
        return pool
            .shuffled()
            .take(12)
            .map { it.toSearchResult() }
    }

    fun sourceById(sourceId: String): PresetNovelSource? = presetSources.firstOrNull { it.sourceId == sourceId }

    private fun novel(
        title: String,
        author: String,
        category: String,
        sizeKb: Int,
        sourceIds: List<String>,
        monthScore: Int,
        yearScore: Int,
        totalScore: Int,
        summary: String
    ): CatalogNovel {
        return CatalogNovel(
            title = title,
            author = author,
            category = category,
            status = BookStatus.FINISHED,
            sizeKb = sizeKb,
            sourceIds = sourceIds,
            monthScore = monthScore,
            yearScore = yearScore,
            totalScore = totalScore,
            summary = summary
        )
    }

    private fun CatalogNovel.score(period: RankingPeriod): Int {
        return when (period) {
            RankingPeriod.MONTH -> monthScore
            RankingPeriod.YEAR -> yearScore
            RankingPeriod.TOTAL -> totalScore
        }
    }

    private fun CatalogNovel.toSearchResult(period: RankingPeriod? = null): SearchResult {
        val source = sourceIds.mapNotNull(::sourceById).firstOrNull()
        val sourceId = source?.sourceId ?: sourceIds.first()
        val scoreText = period?.let { " · ${it.label}综合分 ${score(it)}" }.orEmpty()
        val downloadText = if (source?.supportsDownloadHint == true) " · 原站可能提供下载入口" else ""
        return SearchResult(
            sourceId = sourceId,
            title = title,
            author = author,
            bookUrl = source?.baseUrl.orEmpty(),
            summary = "$category · ${sizeKb}KB · 已完结$scoreText$downloadText\n$summary",
            capabilities = setOf(SourceCapability.OPEN_ORIGINAL) +
                if (source?.supportsDownloadHint == true) setOf(SourceCapability.TXT_IMPORT) else emptySet()
        )
    }

    private fun String.normalizeForMatch(): String = trim().lowercase().replace(" ", "")
}
