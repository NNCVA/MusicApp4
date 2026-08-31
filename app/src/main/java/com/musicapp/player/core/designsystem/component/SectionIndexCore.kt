package com.musicapp.player.core.designsystem.component

import com.ibm.icu.text.Transliterator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SectionSortOrder {
    ASCENDING,
    DESCENDING,
}

const val SECTION_INDEX_DIGIT_LABEL = "0"
const val SECTION_INDEX_OTHER_LABEL = "#"
const val SECTION_INDEX_BUCKET_COUNT = 28

val SECTION_INDEX_ASCENDING_LABELS: List<String> =
    listOf(SECTION_INDEX_DIGIT_LABEL) + ('A'..'Z').map(Char::toString) + listOf(SECTION_INDEX_OTHER_LABEL)

val SECTION_INDEX_DESCENDING_LABELS: List<String> =
    listOf(SECTION_INDEX_OTHER_LABEL) + ('Z' downTo 'A').map(Char::toString) + listOf(SECTION_INDEX_DIGIT_LABEL)

val ASCENDING_LABEL_INDEX_MAP: Map<String, Int> =
    SECTION_INDEX_ASCENDING_LABELS.mapIndexed { idx, label -> label to idx }.toMap()

val DESCENDING_LABEL_INDEX_MAP: Map<String, Int> =
    SECTION_INDEX_DESCENDING_LABELS.mapIndexed { idx, label -> label to idx }.toMap()

fun sectionIndexLabelsForOrder(order: SectionSortOrder): List<String> =
    when (order) {
        SectionSortOrder.ASCENDING -> SECTION_INDEX_ASCENDING_LABELS
        SectionSortOrder.DESCENDING -> SECTION_INDEX_DESCENDING_LABELS
    }

private fun isAllAscii(text: String): Boolean {
    for (i in 0 until text.length) {
        if (text[i].code >= 128) return false
    }
    return true
}

private const val NO_PINYIN_INITIAL: Char = '\u0000'
private const val PINYIN_CACHE_MAGIC = 0x50494E59 // "PINY"
private const val PINYIN_CACHE_VERSION = 1

private val pinyinInitialCache = java.util.concurrent.ConcurrentHashMap<String, Char>()
private val pinyinSortKeyCache = java.util.concurrent.ConcurrentHashMap<String, String>()
private var diskCacheFile: java.io.File? = null
private val isDiskCacheDirty = java.util.concurrent.atomic.AtomicBoolean(false)
private val diskCacheScope = CoroutineScope(Dispatchers.IO)

fun initPinyinDiskCache(baseDir: java.io.File) {
    val file = java.io.File(baseDir, "pinyin_cache.bin")
    diskCacheFile = file
    if (file.exists() && file.length() > 0) {
        runCatching {
            java.io.DataInputStream(java.io.BufferedInputStream(java.io.FileInputStream(file))).use { dis ->
                val magic = dis.readInt()
                val version = dis.readInt()
                if (magic == PINYIN_CACHE_MAGIC && version == PINYIN_CACHE_VERSION) {
                    val count = dis.readInt()
                    for (i in 0 until count) {
                        val text = dis.readUTF()
                        val initial = dis.readChar()
                        val sortKey = dis.readUTF()
                        pinyinInitialCache[text] = initial
                        pinyinSortKeyCache[text] = sortKey
                    }
                }
            }
        }
    }
}

fun savePinyinDiskCache() {
    val file = diskCacheFile ?: return
    isDiskCacheDirty.set(false)
    runCatching {
        val parent = file.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
        val tempFile = java.io.File(parent, "${file.name}.tmp")
        val keys = pinyinSortKeyCache.keys().toList()
        java.io.DataOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(tempFile))).use { dos ->
            dos.writeInt(PINYIN_CACHE_MAGIC)
            dos.writeInt(PINYIN_CACHE_VERSION)
            dos.writeInt(keys.size)
            for (text in keys) {
                dos.writeUTF(text)
                dos.writeChar((pinyinInitialCache[text] ?: NO_PINYIN_INITIAL).code)
                dos.writeUTF(pinyinSortKeyCache[text].orEmpty())
            }
        }
        if (tempFile.exists()) {
            if (file.exists()) file.delete()
            tempFile.renameTo(file)
        }
    }
}

private fun scheduleSavePinyinDiskCache() {
    if (diskCacheFile == null) return
    if (isDiskCacheDirty.compareAndSet(false, true)) {
        diskCacheScope.launch {
            delay(500)
            savePinyinDiskCache()
        }
    }
}

fun warmupPinyinEngine() {
    runCatching {
        classifySectionLabel("音")
        pinyinSortKey("音乐")
    }
}

fun classifySectionLabel(value: String?): String {
    val trimmed = value.orEmpty().trim()
    if (trimmed.isEmpty()) return SECTION_INDEX_OTHER_LABEL
    val firstCodePoint = trimmed.codePointAt(0)
    return when {
        Character.isDigit(firstCodePoint) -> SECTION_INDEX_DIGIT_LABEL
        firstCodePoint in 'A'.code..'Z'.code ->
            String(Character.toChars(firstCodePoint))
        firstCodePoint in 'a'.code..'z'.code ->
            String(Character.toChars(firstCodePoint)).uppercase(Locale.ROOT)
        else -> {
            val charText = String(Character.toChars(firstCodePoint))
            pinyinInitial(charText)?.toString() ?: SECTION_INDEX_OTHER_LABEL
        }
    }
}

fun pinyinSortKey(value: String?): String {
    val trimmed = value.orEmpty().trim()
    if (trimmed.isEmpty()) return ""
    val existing = pinyinSortKeyCache[trimmed]
    if (existing != null) return existing
    if (isAllAscii(trimmed)) {
        val lower = trimmed.lowercase(Locale.ROOT)
        pinyinSortKeyCache[trimmed] = lower
        return lower
    }
    val computed = HAN_TO_LATIN.transliterate(trimmed).lowercase(Locale.ROOT)
    pinyinSortKeyCache[trimmed] = computed
    scheduleSavePinyinDiskCache()
    return computed
}

fun sectionBucketOrder(label: String, order: SectionSortOrder = SectionSortOrder.ASCENDING): Int {
    val map = if (order == SectionSortOrder.ASCENDING) ASCENDING_LABEL_INDEX_MAP else DESCENDING_LABEL_INDEX_MAP
    return map[label] ?: (map.size - 1)
}

fun <T> createSectionTextComparator(
    order: SectionSortOrder,
    textSelector: (T) -> String?,
    tieBreaker: Comparator<T>,
): Comparator<T> {
    val labelMap = if (order == SectionSortOrder.ASCENDING) ASCENDING_LABEL_INDEX_MAP else DESCENDING_LABEL_INDEX_MAP
    val fallbackIndex = labelMap.size - 1
    val bucketComparator = Comparator<T> { a, b ->
        val labelA = classifySectionLabel(textSelector(a))
        val labelB = classifySectionLabel(textSelector(b))
        val indexA = labelMap[labelA] ?: fallbackIndex
        val indexB = labelMap[labelB] ?: fallbackIndex
        indexA.compareTo(indexB)
    }
    val inBucketPinyinComparator = Comparator<T> { a, b ->
        val keyA = pinyinSortKey(textSelector(a))
        val keyB = pinyinSortKey(textSelector(b))
        keyA.compareTo(keyB)
    }
    val inBucketRawComparator = Comparator<T> { a, b ->
        val rawA = textSelector(a).orEmpty().lowercase(Locale.ROOT)
        val rawB = textSelector(b).orEmpty().lowercase(Locale.ROOT)
        rawA.compareTo(rawB)
    }
    val inBucketDirected =
        if (order == SectionSortOrder.ASCENDING) {
            inBucketPinyinComparator.then(inBucketRawComparator)
        } else {
            inBucketPinyinComparator.reversed().then(inBucketRawComparator.reversed())
        }
    val directedTieBreaker = if (order == SectionSortOrder.ASCENDING) tieBreaker else tieBreaker.reversed()
    return bucketComparator.then(inBucketDirected).then(directedTieBreaker)
}

fun <T> List<T>.sortedBySectionText(
    order: SectionSortOrder,
    textSelector: (T) -> String?,
    tieBreaker: Comparator<T>,
): List<T> {
    if (size <= 1) return this
    val labelMap = if (order == SectionSortOrder.ASCENDING) ASCENDING_LABEL_INDEX_MAP else DESCENDING_LABEL_INDEX_MAP
    val fallbackIndex = labelMap.size - 1

    class SortPayload(
        val item: T,
        val bucket: Int,
        val pinyin: String,
        val rawLower: String,
    )

    val payloads = ArrayList<SortPayload>(size)
    for (item in this) {
        val raw = textSelector(item).orEmpty().trim()
        val label = classifySectionLabel(raw)
        val bucket = labelMap[label] ?: fallbackIndex
        val pinyin = pinyinSortKey(raw)
        val rawLower = raw.lowercase(Locale.ROOT)
        payloads.add(SortPayload(item, bucket, pinyin, rawLower))
    }

    val payloadComparator = Comparator<SortPayload> { a, b ->
        val bucketCmp = a.bucket.compareTo(b.bucket)
        if (bucketCmp != 0) return@Comparator bucketCmp

        val pinyinCmp = if (order == SectionSortOrder.ASCENDING) {
            a.pinyin.compareTo(b.pinyin)
        } else {
            b.pinyin.compareTo(a.pinyin)
        }
        if (pinyinCmp != 0) return@Comparator pinyinCmp

        val rawCmp = if (order == SectionSortOrder.ASCENDING) {
            a.rawLower.compareTo(b.rawLower)
        } else {
            b.rawLower.compareTo(a.rawLower)
        }
        if (rawCmp != 0) return@Comparator rawCmp

        if (order == SectionSortOrder.ASCENDING) {
            tieBreaker.compare(a.item, b.item)
        } else {
            tieBreaker.compare(b.item, a.item)
        }
    }

    payloads.sortWith(payloadComparator)
    return payloads.map { it.item }
}

private val HAN_TO_LATIN: Transliterator by lazy {
    Transliterator.getInstance("Han-Latin")
}

private fun pinyinInitial(text: String): Char? {
    val cached = pinyinInitialCache[text]
    if (cached != null) {
        return if (cached == NO_PINYIN_INITIAL) null else cached
    }
    val computed = HAN_TO_LATIN.transliterate(text)
        .firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }
        ?.uppercaseChar()
        ?.takeIf { it in 'A'..'Z' }
    pinyinInitialCache[text] = computed ?: NO_PINYIN_INITIAL
    scheduleSavePinyinDiskCache()
    return computed
}

fun mapPointerYToBucketIndex(
    pointerY: Float,
    indexTop: Float,
    indexBottom: Float,
    bucketCount: Int = SECTION_INDEX_BUCKET_COUNT,
): Int {
    val span = (indexBottom - indexTop).coerceAtLeast(1f)
    val normalizedY = ((pointerY - indexTop) / span).coerceIn(0f, 1f)
    val index = (normalizedY * bucketCount).toInt()
    return index.coerceIn(0, bucketCount - 1)
}

/**
 * 寻找最近非空桶。
 *
 * @param targetBucketIndex 目标桶序号 (0..bucketCount-1)
 * @param populatedBucketIndices 存在数据的桶序号集合
 * @param dragDirection 拖动方向：> 0 表示向下/正向移动，< 0 表示向上/反向移动，0 表示无方向倾向
 */
fun resolveNearestPopulatedBucket(
    targetBucketIndex: Int,
    populatedBucketIndices: Set<Int>,
    dragDirection: Int = 0,
    bucketCount: Int = SECTION_INDEX_BUCKET_COUNT,
): Int {
    if (populatedBucketIndices.isEmpty()) {
        return targetBucketIndex.coerceIn(0, (bucketCount - 1).coerceAtLeast(0))
    }
    if (targetBucketIndex in populatedBucketIndices) {
        return targetBucketIndex
    }

    var bestIndex = populatedBucketIndices.first()
    var minDistance = abs(bestIndex - targetBucketIndex)

    for (candidate in populatedBucketIndices) {
        val distance = abs(candidate - targetBucketIndex)
        if (distance < minDistance) {
            minDistance = distance
            bestIndex = candidate
        } else if (distance == minDistance) {
            // 等距时优先当前拖动方向；默认正向优先
            if (dragDirection >= 0 && candidate > bestIndex) {
                bestIndex = candidate
            } else if (dragDirection < 0 && candidate < bestIndex) {
                bestIndex = candidate
            }
        }
    }

    return bestIndex
}

/**
 * 动态等距采样可见标签。
 *
 * 满足：maxVisibleLabels = clamp(floor(H / 12dp), 4, 28)
 * 空间足够时展示全部 28 个；不足时保留首尾桶及当前方向的 A/Z 边界，并在中间等距采样。
 */
fun sampleVisibleLabels(
    availableHeightDp: Float,
    itemSizeDp: Float = 12f,
    labels: List<String> = SECTION_INDEX_ASCENDING_LABELS,
): List<String> {
    val totalCount = labels.size
    if (totalCount <= 4) return labels

    val maxVisible = floor(availableHeightDp / itemSizeDp).toInt().coerceIn(4, totalCount)
    if (maxVisible >= totalCount) return labels

    val firstIndex = 0
    val lastIndex = totalCount - 1
    val aIndex = labels.indexOf("A").takeIf { it >= 0 }
    val zIndex = labels.indexOf("Z").takeIf { it >= 0 }

    val sampledIndices = sortedSetOf<Int>()
    sampledIndices.add(firstIndex)
    sampledIndices.add(lastIndex)
    aIndex?.let { sampledIndices.add(it) }
    zIndex?.let { sampledIndices.add(it) }

    // 在 0..lastIndex 范围内等距采样
    val step = (lastIndex - firstIndex).toFloat() / (maxVisible - 1).coerceAtLeast(1)
    for (i in 0 until maxVisible) {
        val idx = (firstIndex + i * step).roundToInt().coerceIn(firstIndex, lastIndex)
        sampledIndices.add(idx)
    }

    // 如果因边界添加导致超过 maxVisible，适度剔除非关键项
    val resultIndices = if (sampledIndices.size > maxVisible) {
        val keep = sortedSetOf<Int>()
        keep.add(firstIndex)
        keep.add(lastIndex)
        val remainingSlots = maxVisible - keep.size
        val innerCandidates = sampledIndices.filter { it !in keep }
        if (innerCandidates.isNotEmpty()) {
            val innerStep = innerCandidates.size.toFloat() / remainingSlots.coerceAtLeast(1)
            for (j in 0 until remainingSlots) {
                val cIdx = (j * innerStep).toInt().coerceIn(0, innerCandidates.lastIndex)
                keep.add(innerCandidates[cIdx])
            }
        }
        keep
    } else {
        sampledIndices
    }

    return resultIndices.map { labels[it] }
}
