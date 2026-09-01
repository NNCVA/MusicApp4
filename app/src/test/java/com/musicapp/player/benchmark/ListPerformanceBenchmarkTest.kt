package com.musicapp.player.benchmark

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.image.ArtworkReadLimiter
import com.musicapp.player.feature.albums.AlbumGrouping
import com.musicapp.player.feature.albums.AlbumSort
import com.musicapp.player.feature.albums.AlbumSortField
import com.musicapp.player.feature.artists.ArtistGrouping
import com.musicapp.player.feature.tracks.TrackSection
import com.musicapp.player.feature.tracks.TrackSort
import com.musicapp.player.feature.tracks.TrackSortDirection
import com.musicapp.player.feature.tracks.TrackSortField
import com.musicapp.player.feature.tracks.groupTracksIntoSections
import com.musicapp.player.feature.tracks.sectionStartPositions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.system.measureNanoTime

class ListPerformanceBenchmarkTest {

    private fun generateMockTracks(count: Int): List<Track> {
        val artistPool = listOf(
            "周杰伦", "Taylor Swift", "陈奕迅", "Ed Sheeran", "林俊杰",
            "Billie Eilish", "邓紫棋", "Adele", "莫文蔚", "Bruno Mars",
            "王力宏 / 陶喆", "张学友, 刘德华", "Coldplay; BTS", "李荣浩、薛之谦",
        )
        val albumPool = listOf(
            "范特西", "1989", "U87", "Divide", "江南",
            "Happier Than Ever", "光年之外", "21", "盛夏的果实", "24K Magic",
            "魔杰座", "十一月的萧邦", "叶惠美", "八度空间", "Jay",
        )
        val titlePrefixPool = listOf(
            "夜曲", "晴天", "Blank Space", "十年", "Shape of You",
            "江南", "Bad Guy", "泡沫", "Rolling in the Deep", "阴天",
            "Uptown Funk", "七里香", "稻香", "青花瓷", "告白气球",
            "安河桥", "南山南", "起风了", "消愁", "像我这样的人",
        )

        return (1..count).map { index ->
            val artist = artistPool[index % artistPool.size]
            val album = albumPool[index % albumPool.size]
            val title = "${titlePrefixPool[index % titlePrefixPool.size]} $index"
            val albumId = AlbumId("primary", (index % 150).toLong() + 1)

            Track(
                id = TrackId("primary", index.toLong()),
                title = title,
                artistName = artist,
                artistMediaStoreId = (index % 100).toLong() + 1,
                albumTitle = album,
                albumId = albumId,
                durationMs = 180_000L + (index % 120) * 1000L,
                dateAddedMs = 1_700_000_000_000L + index * 10_000L,
                dateModifiedMs = 1_700_000_000_000L + index * 10_000L,
                relativePath = "Music/Folder${index % 20}/",
                displayName = "$title.mp3",
                mimeType = "audio/mp3",
                sizeBytes = 10_000_000L + index * 1000L,
                availability = Availability.AVAILABLE,
            )
        }
    }

    @Test
    fun benchmarkTrackSortingAndSectionIndexing() {
        val trackCount = 3000
        val tracks = generateMockTracks(trackCount)

        println("\n=======================================================")
        println(" [BENCHMARK] Tracks 列表加载、排序与索引构建 ($trackCount 首)")
        println("=======================================================")

        // 1. 预热
        groupTracksIntoSections(tracks.take(50), TrackSortField.TITLE, TrackSortDirection.ASCENDING)

        // 2. 标题排序与 Section 构建
        var sectionsTitle: List<TrackSection>
        var positionsTitle: Map<String, Int>
        val timeTitleNs = measureNanoTime {
            sectionsTitle = groupTracksIntoSections(tracks, TrackSortField.TITLE, TrackSortDirection.ASCENDING)
            positionsTitle = sectionStartPositions(sectionsTitle, TrackSortDirection.ASCENDING)
        }
        val timeTitleMs = timeTitleNs / 1_000_000.0
        println("▶ 标题排序 (TITLE + ASC): %.2f ms (生成 %d 个分区, %d 个锚点)".format(timeTitleMs, sectionsTitle.size, positionsTitle.size))

        // 3. 艺术家排序与 Section 构建
        var sectionsArtist: List<TrackSection>
        val timeArtistNs = measureNanoTime {
            sectionsArtist = groupTracksIntoSections(tracks, TrackSortField.ARTIST, TrackSortDirection.ASCENDING)
        }
        val timeArtistMs = timeArtistNs / 1_000_000.0
        println("▶ 艺术家排序 (ARTIST + ASC): %.2f ms (生成 %d 个分区)".format(timeArtistMs, sectionsArtist.size))

        // 4. 专辑排序与 Section 构建
        var sectionsAlbum: List<TrackSection>
        val timeAlbumNs = measureNanoTime {
            sectionsAlbum = groupTracksIntoSections(tracks, TrackSortField.ALBUM, TrackSortDirection.ASCENDING)
        }
        val timeAlbumMs = timeAlbumNs / 1_000_000.0
        println("▶ 专辑排序 (ALBUM + ASC): %.2f ms (生成 %d 个分区)".format(timeAlbumMs, sectionsAlbum.size))
    }

    @Test
    fun benchmarkAlbumGroupingAndSorting() {
        val trackCount = 3000
        val tracks = generateMockTracks(trackCount)

        println("\n=======================================================")
        println(" [BENCHMARK] Albums 专辑分组与排序 ($trackCount 首歌曲)")
        println("=======================================================")

        // 预热
        AlbumGrouping.group(tracks.take(50))

        // 分组
        val groupNs = measureNanoTime {
            val albums = AlbumGrouping.group(tracks)
            assert(albums.isNotEmpty())
        }
        val groupMs = groupNs / 1_000_000.0
        println("▶ AlbumGrouping.group(): %.2f ms".format(groupMs))

        val albums = AlbumGrouping.group(tracks)

        // 标题排序
        val sortTitleNs = measureNanoTime {
            AlbumGrouping.sorted(albums, AlbumSort(field = AlbumSortField.TITLE))
        }
        println("▶ AlbumGrouping.sorted(TITLE): %.2f ms".format(sortTitleNs / 1_000_000.0))

        // 艺术家排序
        val sortArtistNs = measureNanoTime {
            AlbumGrouping.sorted(albums, AlbumSort(field = AlbumSortField.ARTIST))
        }
        println("▶ AlbumGrouping.sorted(ARTIST): %.2f ms".format(sortArtistNs / 1_000_000.0))
    }

    @Test
    fun benchmarkArtistGroupingWithDelimiters() {
        val trackCount = 3000
        val tracks = generateMockTracks(trackCount)

        println("\n=======================================================")
        println(" [BENCHMARK] Artists 正则拆分、聚合与拼音排序 ($trackCount 首歌曲)")
        println("=======================================================")

        // 预热
        ArtistGrouping.group(tracks.take(50))

        val groupNs = measureNanoTime {
            val artists = ArtistGrouping.group(tracks)
            assert(artists.isNotEmpty())
        }
        val groupMs = groupNs / 1_000_000.0
        println("▶ ArtistGrouping.group() (正则分割 + 归一 + 排序): %.2f ms".format(groupMs))
    }

    @Test
    fun benchmarkArtworkReadConcurrencyAndLimiter() = runBlocking(Dispatchers.Default) {
        val limiter = ArtworkReadLimiter()
        val requestCount = 50

        println("\n=======================================================")
        println(" [BENCHMARK] 图片管道并发模拟 (50 个并发 Item 滑入)")
        println("=======================================================")

        // 模拟 50 个连续 Item 触发提取，每次提取模拟 I/O 与解码耗时 8ms
        val timeNs = measureNanoTime {
            val jobs = (1..requestCount).map {
                async {
                    limiter.withPermit {
                        delay(8) // 模拟解码与IO耗时
                    }
                }
            }
            jobs.awaitAll()
        }
        val timeMs = timeNs / 1_000_000.0
        println("▶ 50 项封面请求在并发限制下的总处理耗时: %.2f ms (每项平均模拟耗时 8ms)".format(timeMs))
    }
}
