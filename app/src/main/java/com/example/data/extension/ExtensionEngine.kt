package com.example.data.extension

import com.example.data.local.CategoryEntity
import com.example.data.local.ChapterEntity
import com.example.data.local.ExtensionRepoEntity
import com.example.data.local.ExtensionSourceEntity
import com.example.data.local.MangaEntity

object ExtensionEngine {

    val defaultCategories = listOf(
        CategoryEntity("c1", "Reading", 1),
        CategoryEntity("c2", "Favorites", 2),
        CategoryEntity("c3", "Manhwa", 3),
        CategoryEntity("c4", "Manga", 4),
        CategoryEntity("c5", "Completed", 5),
        CategoryEntity("c6", "Plan to Read", 6)
    )

    val defaultRepos = listOf(
        ExtensionRepoEntity(
            id = "mihon-official",
            name = "Mihon Official Extension Repo",
            url = "https://raw.githubusercontent.com/mihonapp/mihon-extensions/repo/index.json",
            extensionCount = 142,
            isOfficial = true
        ),
        ExtensionRepoEntity(
            id = "aniyomi-anime-manga",
            name = "Aniyomi Extensions Repository",
            url = "https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo/index.json",
            extensionCount = 98,
            isOfficial = true
        ),
        ExtensionRepoEntity(
            id = "keiyoushi-community",
            name = "Keiyoushi Community Extension Repo",
            url = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json",
            extensionCount = 310,
            isOfficial = false
        )
    )

    val defaultSources = listOf(
        ExtensionSourceEntity(
            id = "asura",
            name = "Asura Scans",
            version = "1.4.2",
            lang = "en",
            iconUrl = "https://picsum.photos/seed/asura_logo/120/120",
            repoId = "mihon-official",
            isInstalled = true,
            isNsfw = false,
            baseUrl = "https://asuracomic.net",
            sourceType = "MANHWA"
        ),
        ExtensionSourceEntity(
            id = "flame",
            name = "Flame Comics",
            version = "2.1.0",
            lang = "en",
            iconUrl = "https://picsum.photos/seed/flame_logo/120/120",
            repoId = "mihon-official",
            isInstalled = true,
            isNsfw = false,
            baseUrl = "https://flamecomics.me",
            sourceType = "MANHWA"
        ),
        ExtensionSourceEntity(
            id = "mangadex",
            name = "MangaDex",
            version = "2.0.18",
            lang = "en",
            iconUrl = "https://picsum.photos/seed/mangadex_logo/120/120",
            repoId = "mihon-official",
            isInstalled = true,
            isNsfw = false,
            baseUrl = "https://mangadex.org",
            sourceType = "MANGA"
        ),
        ExtensionSourceEntity(
            id = "webtoon",
            name = "WEBTOON Official",
            version = "1.1.5",
            lang = "en",
            iconUrl = "https://picsum.photos/seed/webtoon_logo/120/120",
            repoId = "aniyomi-anime-manga",
            isInstalled = true,
            isNsfw = false,
            baseUrl = "https://www.webtoons.com",
            sourceType = "MANHWA"
        ),
        ExtensionSourceEntity(
            id = "reaper",
            name = "Reaper Scans",
            version = "1.8.0",
            lang = "en",
            iconUrl = "https://picsum.photos/seed/reaper_logo/120/120",
            repoId = "keiyoushi-community",
            isInstalled = true,
            isNsfw = false,
            baseUrl = "https://reaperscans.com",
            sourceType = "MANHWA"
        ),
        ExtensionSourceEntity(
            id = "anilist",
            name = "AniList Tracker Source Bridge",
            version = "3.0.0",
            lang = "all",
            iconUrl = "https://picsum.photos/seed/anilist_logo/120/120",
            repoId = "mihon-official",
            isInstalled = true,
            isNsfw = false,
            baseUrl = "https://anilist.co",
            sourceType = "MULTI"
        )
    )

    // Pre-populated Catalog Titles across sources
    val sampleCatalog = listOf(
        MangaEntity(
            id = "asura:solo-leveling",
            title = "Solo Leveling (Arise)",
            coverUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80",
            author = "Chugong",
            artist = "DUBU (REDICE Studio)",
            description = "10 years ago, after 'the Gate' connected the real world with the monster world, some of the ordinary people received the power to hunt monsters. They're known as Hunters. However, Sung Jinwoo is known as the 'Weakest E-Rank Hunter'. Following a double dungeon incident, he gains a mysterious System window that grants him the unique ability to Level Up without limits!",
            sourceId = "asura",
            sourceName = "Asura Scans",
            status = "COMPLETED",
            type = "MANHWA",
            inLibrary = true,
            category = "Favorites",
            unreadCount = 12,
            bookmarkCount = 180,
            rating = 4.95f,
            genres = "Action, Fantasy, System, Necromancer, Dungeon"
        ),
        MangaEntity(
            id = "asura:omniscient-reader",
            title = "Omniscient Reader's Viewpoint",
            coverUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=600&auto=format&fit=crop&q=80",
            author = "sing N song",
            artist = "Sleepy-C (REDICE Studio)",
            description = "Dokja was an average office worker whose sole interest was reading his favorite web novel 'Three Ways to Survive the Apocalypse'. But when the novel suddenly becomes reality, he is the only person who knows how the world will end. Armed with his knowledge, Dokja uses his understanding to change the course of the story and the world!",
            sourceId = "asura",
            sourceName = "Asura Scans",
            status = "ONGOING",
            type = "MANHWA",
            inLibrary = true,
            category = "Reading",
            unreadCount = 5,
            bookmarkCount = 145,
            rating = 4.92f,
            genres = "Action, Apocalypse, System, Psychological, Constellation"
        ),
        MangaEntity(
            id = "flame:pick-me-up",
            title = "Pick Me Up! Infinite Gacha",
            coverUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=600&auto=format&fit=crop&q=80",
            author = "Hermod",
            artist = "WASAB",
            description = "In the mobile gacha game 'Pick Me Up', Loki was the 5th ranked master in the world. Losing consciousness while clearing a dungeon, he wakes up transformed into a 1-Star mob character inside the game. To survive and return home, he must train rookie heroes and conquer all 100 floors of the dungeon!",
            sourceId = "flame",
            sourceName = "Flame Comics",
            status = "ONGOING",
            type = "MANHWA",
            inLibrary = true,
            category = "Manhwa",
            unreadCount = 3,
            bookmarkCount = 92,
            rating = 4.88f,
            genres = "Action, Gacha, Survival, Strategy, Tower"
        ),
        MangaEntity(
            id = "asura:beginning-after-end",
            title = "The Beginning After the End",
            coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80",
            author = "TurtleMe",
            artist = "Fuyuki23 / MG",
            description = "King Grey has unrivaled strength, wealth, and prestige in a world governed by martial ability. However, solitude lingers closely behind those with great power. Reincarnated into a new world filled with magic and monsters, Arthur Leywin gets a second chance to relive his life and protect those he loves.",
            sourceId = "asura",
            sourceName = "Asura Scans",
            status = "ONGOING",
            type = "MANHWA",
            inLibrary = false,
            category = "Reading",
            unreadCount = 0,
            bookmarkCount = 210,
            rating = 4.89f,
            genres = "Reincarnation, Magic, Adventure, Action, Romance"
        ),
        MangaEntity(
            id = "mangadex:chainsaw-man",
            title = "Chainsaw Man",
            coverUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600&auto=format&fit=crop&q=80",
            author = "Tatsuki Fujimoto",
            artist = "Tatsuki Fujimoto",
            description = "Denji was a young man living a poverty-stricken life, paying off his deceased father's debt by harvesting devil corpses with Pochita, the Chainsaw Devil. One day, Denji is betrayed and killed, but Pochita becomes his heart, resurrecting him as Chainsaw Man!",
            sourceId = "mangadex",
            sourceName = "MangaDex",
            status = "ONGOING",
            type = "MANGA",
            inLibrary = true,
            category = "Manga",
            unreadCount = 8,
            bookmarkCount = 320,
            rating = 4.90f,
            genres = "Action, Horror, Dark Fantasy, Supernatural, Comedy"
        ),
        MangaEntity(
            id = "webtoon:tower-of-god",
            title = "Tower of God",
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
            author = "SIU",
            artist = "SIU",
            description = "What do you desire? Money and wealth? Honor and pride? Authority and power? Revenge? Or something that transcends all of them? Whatever you desire, it is at the top of the Tower. Bam, a boy who knew nothing but Rachel, opens the gate of the Tower to chase after her...",
            sourceId = "webtoon",
            sourceName = "WEBTOON Official",
            status = "ONGOING",
            type = "MANHWA",
            inLibrary = true,
            category = "Manhwa",
            unreadCount = 24,
            bookmarkCount = 410,
            rating = 4.86f,
            genres = "Action, Mystery, Tower, World Building, Fantasy"
        ),
        MangaEntity(
            id = "flame:swordmaster-son",
            title = "Swordmaster's Youngest Son",
            coverUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=600&auto=format&fit=crop&q=80",
            author = "AZI",
            artist = "CoCo (REDICE Studio)",
            description = "Jin Runcandel was the youngest son of Runcandel, the land's greatest swordmaster family... and the worst failure in Runcandel history. After being banished and killed, he is given a second chance by the Shadow God Solderat!",
            sourceId = "flame",
            sourceName = "Flame Comics",
            status = "ONGOING",
            type = "MANHWA",
            inLibrary = false,
            category = "Reading",
            unreadCount = 0,
            bookmarkCount = 78,
            rating = 4.87f,
            genres = "Swordmaster, Reincarnation, Magic, Family, Revenge"
        ),
        MangaEntity(
            id = "reaper:mount-hua-sect",
            title = "Return of the Mount Hua Sect",
            coverUrl = "https://images.unsplash.com/photo-1514539079130-25950c84af65?w=600&auto=format&fit=crop&q=80",
            author = "Biga",
            artist = "LICO",
            description = "Chung Myung, the 13th Disciple of the Great Mount Hua Sect, defeated the Heavenly Demon in mortal combat and died atop the 100,000 Mountains. Reincarnated 100 years later, he discovers his beloved Mount Hua Sect in ruin! He vows to revive it to its former glory!",
            sourceId = "reaper",
            sourceName = "Reaper Scans",
            status = "ONGOING",
            type = "MANHWA",
            inLibrary = false,
            category = "Reading",
            unreadCount = 0,
            bookmarkCount = 165,
            rating = 4.94f,
            genres = "Murim, Comedy, Reincarnation, Martial Arts, Action"
        )
    )

    fun getSampleChaptersForManga(mangaId: String): List<ChapterEntity> {
        val list = mutableListOf<ChapterEntity>()
        val total = when {
            mangaId.contains("solo-leveling") -> 179
            mangaId.contains("omniscient") -> 215
            mangaId.contains("tower-of-god") -> 620
            mangaId.contains("chainsaw") -> 168
            else -> 120
        }

        // Generate top 25 chapters for immediate reading preview
        for (i in total downTo (total - 24).coerceAtLeast(1)) {
            val isRead = i < (total - 8)
            val chapterName = "Chapter $i - " + when {
                i == total -> "The Final Showdown & Evolution"
                i == total - 1 -> "Awakening of the Monarch"
                i == total - 2 -> "Unstoppable Surge"
                i == total - 3 -> "Domain Expansion & Shadow Army"
                else -> "Clash of Titans (Part ${i % 4 + 1})"
            }
            list.add(
                ChapterEntity(
                    id = "$mangaId:ch-$i",
                    mangaId = mangaId,
                    chapterNumber = i.toFloat(),
                    name = chapterName,
                    scanlator = if (mangaId.contains("asura")) "Asura Scans" else "Official Release",
                    releaseDate = "${(total - i) * 3 + 1} days ago",
                    read = isRead,
                    bookmarked = (i % 10 == 0),
                    lastPageRead = if (isRead) 24 else 1,
                    totalPages = 24,
                    fetchUrl = "https://nekoread.api/chapter/$mangaId/$i"
                )
            )
        }
        return list
    }

    // High quality rendered vertical strips / pages for reader
    fun getChapterPages(mangaId: String, chapterNumber: Float): List<String> {
        val baseSeed = (mangaId.hashCode() + chapterNumber.toInt()).toString()
        val pages = mutableListOf<String>()
        // Return 18 high-res vertical webtoon panel pages with realistic artistic gradient strips
        for (page in 1..18) {
            val imageUrl = when (page % 6) {
                0 -> "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1000&auto=format&fit=crop&q=80"
                1 -> "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1000&auto=format&fit=crop&q=80"
                2 -> "https://images.unsplash.com/photo-1563089145-599997674d42?w=1000&auto=format&fit=crop&q=80"
                3 -> "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1000&auto=format&fit=crop&q=80"
                4 -> "https://images.unsplash.com/photo-1514539079130-25950c84af65?w=1000&auto=format&fit=crop&q=80"
                else -> "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=1000&auto=format&fit=crop&q=80"
            }
            pages.add(imageUrl)
        }
        return pages
    }
}
