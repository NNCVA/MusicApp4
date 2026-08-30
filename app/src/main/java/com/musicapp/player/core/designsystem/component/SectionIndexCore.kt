package com.musicapp.player.core.designsystem.component

import com.ibm.icu.text.Transliterator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

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

fun sectionIndexLabelsForOrder(order: SectionSortOrder): List<String> =
    when (order) {
        SectionSortOrder.ASCENDING -> SECTION_INDEX_ASCENDING_LABELS
        SectionSortOrder.DESCENDING -> SECTION_INDEX_DESCENDING_LABELS
    }

fun classifySectionLabel(value: String?): String {
    val trimmed = value.orEmpty().trim()
    if (trimmed.isEmpty()) return SECTION_INDEX_OTHER_LABEL
    val firstCodePoint = trimmed.codePointAt(0)
    return when {
        Character.isDigit(firstCodePoint) -> SECTION_INDEX_DIGIT_LABEL
        firstCodePoint in 'A'.code..'Z'.code || firstCodePoint in 'a'.code..'z'.code ->
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
    return HAN_TO_LATIN.transliterate(trimmed).lowercase(Locale.ROOT)
}

fun sectionBucketOrder(label: String, order: SectionSortOrder = SectionSortOrder.ASCENDING): Int {
    val labels = sectionIndexLabelsForOrder(order)
    val index = labels.indexOf(label)
    return if (index >= 0) index else labels.lastIndex
}

fun <T> createSectionTextComparator(
    order: SectionSortOrder,
    textSelector: (T) -> String?,
    tieBreaker: Comparator<T>,
): Comparator<T> {
    val labels = sectionIndexLabelsForOrder(order)
    val bucketComparator = Comparator<T> { a, b ->
        val labelA = classifySectionLabel(textSelector(a))
        val labelB = classifySectionLabel(textSelector(b))
        val indexA = labels.indexOf(labelA).let { if (it >= 0) it else labels.lastIndex }
        val indexB = labels.indexOf(labelB).let { if (it >= 0) it else labels.lastIndex }
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

private val HAN_TO_LATIN: Transliterator by lazy {
    Transliterator.getInstance("Han-Latin")
}

private fun pinyinInitial(text: String): Char? =
    HAN_TO_LATIN.transliterate(text)
        .firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }
        ?.uppercaseChar()
        ?.takeIf { it in 'A'..'Z' }

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
