package app.kompakt.flashcards.core

import org.junit.Assert.assertEquals
import org.junit.Test

private var failures = 0
private fun check(name: String, cond: Boolean, detail: Any? = null) {
    if (cond) println("  ok   $name") else { failures++; println("  FAIL $name  ${detail ?: ""}") }
}

private fun runChecks() {
    println("Parser")
    val txt = """
        # Hearsay exceptions
        // a note
        Excited utterance :: Statement relating to a startling event made under the stress of excitement
        - Present sense impression :: Describes an event while perceiving it
        Dying declaration	Statement by declarant believing death imminent

        Q: What are the elements of
           a dying declaration?
        A: 1. Declarant believed death imminent
           2. Concerns cause of death
        Q: Missing answer here

        A: orphan answer
        just some random line
        Business records :: Rule 803(6)
    """.trimIndent()
    val r = DeckParser.parse("Hearsay.md", txt)
    r.cards.forEach { println("     card: ${it.first.replace("\n","⏎")} || ${it.second.replace("\n","⏎")}") }
    r.warnings.forEach { println("     warn: $it") }
    check("5 cards parsed", r.cards.size == 5, r.cards.size)
    check("bullet stripped", r.cards[1].first == "Present sense impression")
    check("tab card", r.cards[2].second.startsWith("Statement by declarant"))
    check("multiline Q", r.cards[3].first == "What are the elements of\na dying declaration?")
    check("multiline A", r.cards[3].second.endsWith("2. Concerns cause of death"))
    check("3 warnings", r.warnings.size == 3, r.warnings)

    val csv = "Front;Back\nhola;hello\n\"adiós; chau\";\"goodbye\nbye\"\nsolo;\n"
    val rc = DeckParser.parse("Spanish.csv", csv)
    check("csv semicolon + header + quoted", rc.cards == listOf("hola" to "hello", "adiós; chau" to "goodbye\nbye"), rc.cards)
    check("csv warning for empty back", rc.warnings.size == 1, rc.warnings)
    val rc2 = DeckParser.parse("x.csv", "﻿cat,gato\r\ndog,perro\r\n")
    check("csv comma + BOM + CRLF", rc2.cards == listOf("cat" to "gato", "dog" to "perro"), rc2.cards)

    val formatted = DeckParser.format(r.cards)
    val back = DeckParser.parse("x.txt", formatted)
    check("format round-trips", back.cards == r.cards, formatted)

    // A blank line inside an answer is kept; a blank line before a new card still separates.
    val spaced = "Q: Hearsay — elements?\nA: • Out-of-court statement\n• Offered for its truth\n\nNote: the law has changed.\n\nQ: Next?\nA: Yes\n\nfront :: back\n\n# heading\nlast :: one"
    val rs = DeckParser.parse("s.txt", spaced)
    check("blank line kept inside an answer", rs.cards == listOf(
        "Hearsay — elements?" to "• Out-of-court statement\n• Offered for its truth\n\nNote: the law has changed.",
        "Next?" to "Yes", "front" to "back", "last" to "one"), rs.cards)
    check("blank-line answers round-trip", DeckParser.parse("s.txt", DeckParser.format(rs.cards)).cards == rs.cards, DeckParser.format(rs.cards))

    println("Import / merge")
    val files = listOf(
        IncomingFile("Law/Evidence/Hearsay.md", txt),
        IncomingFile("Law/Crim Pro.txt", "Miranda :: custodial interrogation warnings\nTerry stop :: reasonable suspicion"),
        IncomingFile("Spanish.csv", "hola,hello\ngato,cat"),
        IncomingFile(".DS_Store", "junk"),
        IncomingFile("Law/notes.pdf", "junk"),
    )
    var (lib, rep) = LibraryData().importFiles(files)
    println("     ${rep.summary}; ignored=${rep.filesIgnored}")
    check("3 decks, 2 folders", lib.decks.size == 3 && lib.folders.size == 2)
    check("nested path", lib.decks.map { lib.deckPath(it) }.toSet() == setOf("Law/Evidence/Hearsay", "Law/Crim Pro", "Spanish"))
    check("ignored 2", rep.filesIgnored.size == 2)
    val law = lib.folders.first { it.name == "Law" }
    check("decksUnder Law = 2", lib.decksUnder(law.id).size == 2)
    check("childFolders root", lib.childFolders(null).map { it.name } == listOf("Law"))

    // review a card, then re-sync an edited file: progress must survive
    val fsrs = Fsrs()
    val now = 1_700_000_000_000L
    val miranda = lib.cards.first { it.front == "Miranda" }
    lib = lib.setReview(miranda.id, fsrs.next(miranda.review, Rating.GOOD, now))
    val edited = IncomingFile("law/crim pro.txt", "miranda :: Warnings before custodial interrogation\nKatz :: reasonable expectation of privacy")
    val (lib2, rep2) = lib.importFiles(listOf(edited))
    println("     ${rep2.summary}")
    val m2 = lib2.cards.first { it.front == "miranda" }
    check("progress kept on edit", m2.id == miranda.id && m2.review.reps == 1)
    check("Terry removed, Katz added", rep2.cardsRemoved == 1 && rep2.cardsAdded == 1 && rep2.cardsChanged == 1, rep2)
    check("case-insensitive folder match (no new folder)", lib2.folders.size == 2)
    check("other decks untouched (merge)", lib2.decks.size == 3)

    val (lib3, rep3) = lib2.importFiles(listOf(IncomingFile("Law/Crim Pro.txt", "Gideon :: right to counsel")), appendOnly = true)
    check("append keeps old cards", lib3.cardsOf(lib3.decks.first { it.name == "Crim Pro" }.id).size == 3, rep3)

    val (lib4, rep4) = lib3.importFiles(listOf(IncomingFile("Spanish.csv", "hola,hello")), mirror = true)
    println("     mirror: ${rep4.summary}")
    check("mirror removes other decks + empty folders", lib4.decks.size == 1 && lib4.folders.isEmpty(), lib4)

    // append keeps order: edited card stays in place, new card goes last
    val (la, _) = LibraryData().importFiles(listOf(IncomingFile("D.txt", "a :: 1\nb :: 2\nc :: 3")))
    val (lb, _) = la.importFiles(listOf(IncomingFile("D.txt", "d :: 4\nb :: 2 edited")), appendOnly = true)
    check("append order", lb.cards.map { it.front + "=" + it.back } == listOf("a=1", "b=2 edited", "c=3", "d=4"), lb.cards.map { it.front })
    // examples shown on the Mac page
    val ex1 = DeckParser.parse("a.txt", "Photosynthesis :: Turning light into food\n- Mitosis :: Cell division\nOsmosis :: Water crossing a membrane")
    check("page example 1", ex1.cards.size == 3 && ex1.warnings.isEmpty() && ex1.cards[1].first == "Mitosis")
    val ex2 = DeckParser.parse("a.txt", "Q: What are the three\nbranches of government?\nA: Legislative, executive\nand judicial.")
    check("page example 2", ex2.cards.size == 1 && ex2.warnings.isEmpty())
    val ex3 = DeckParser.parse("a.csv", "Front,Back\ngato,cat\nperro,dog")
    check("page example 3", ex3.cards == listOf("gato" to "cat", "perro" to "dog"))
    java.io.File("src/main/assets/examples").walkTopDown().filter { it.isFile }.forEach { f ->
        val r = DeckParser.parse(f.name, f.readText())
        check("example '${f.name}' parses cleanly", r.cards.isNotEmpty() && r.warnings.isEmpty(), r.warnings)
    }

    println("Stars, move, search, shuffle")
    run {
        var l = LibraryData().importFiles(listOf(
            IncomingFile("A/One.txt", "cat :: gato\ndog :: perro\nbird :: pajaro"),
            IncomingFile("B.txt", "red :: rojo"),
        )).first
        val dog = l.cards.first { it.front == "dog" }
        l = l.toggleStar(dog.id)
        check("star on", l.starredCards().map { it.front } == listOf("dog"))
        // re-sync keeps the star
        l = l.importFiles(listOf(IncomingFile("A/One.txt", "cat :: gato\ndog :: perro!\nbird :: pajaro"))).first
        check("star survives re-sync", l.cards.first { it.front == "dog" }.starred)
        val b = l.decks.first { it.name == "B" }
        l = l.moveCard(dog.id, b.id)
        val moved = l.cards.first { it.id == dog.id }
        check("move keeps id, star, progress", moved.deckId == b.id && moved.starred)
        check("move puts card last in target", l.cardsOf(b.id).map { it.front } == listOf("red", "dog"))
        check("codec keeps star", LibraryCodec.decode(LibraryCodec.encode(l)) == l)
        val r = l.search("PERR")
        check("search card back, case-insensitive", r.cards.map { it.front } == listOf("dog") && r.decks.isEmpty())
        check("search deck + folder names", l.search("one").decks.size == 1 && l.search("a").folders.size == 1)
        check("empty query", l.search("  ").isEmpty)
        val a = l.folders.first { it.name == "A" }
        check("folder not empty", !l.isFolderEmpty(a.id))
        val ids = l.decks.map { it.id }.toSet()
        val ordered = l.studyQueue(ids, 0L, 10).map { it.front }
        val shuffled = l.studyQueue(ids, 0L, 10, shuffle = true, random = kotlin.random.Random(7)).map { it.front }
        check("shuffle keeps the same cards", ordered.sorted() == shuffled.sorted() && ordered.size == 4)
        check("shuffle changes order", ordered != shuffled, shuffled)
    }

    println("Move deck / folder")
    run {
        var l = LibraryData().importFiles(listOf(
            IncomingFile("A/B/Deck1.txt", "x :: 1"),
            IncomingFile("C/Deck2.txt", "y :: 2"),
        )).first
        val a = l.folders.first { it.name == "A" }; val b = l.folders.first { it.name == "B" }
        val c = l.folders.first { it.name == "C" }
        val d1 = l.decks.first { it.name == "Deck1" }
        l = l.moveDeck(d1.id, c.id)
        check("deck moved, cards kept", l.decks.first { it.id == d1.id }.folderId == c.id && l.cardsOf(d1.id).size == 1)
        l = l.moveDeck(d1.id, null)
        check("deck to top level", l.decks.first { it.id == d1.id }.folderId == null)
        check("can't move folder into itself", !l.canMoveFolderInto(a.id, a.id))
        check("can't move folder into its child", !l.canMoveFolderInto(a.id, b.id))
        val before = l
        check("bad move is ignored", l.moveFolder(a.id, b.id) == before)
        l = l.moveFolder(b.id, c.id)
        check("folder moved", l.folders.first { it.id == b.id }.parentId == c.id)
        l = l.moveFolder(c.id, null)
        check("folder to top level", l.folders.first { it.id == c.id }.parentId == null)
    }

    println("Arrange / folder reset")
    run {
        var l = LibraryData().importFiles(listOf(
            IncomingFile("F/Beta.txt", "b :: 1"),
            IncomingFile("F/Alpha.txt", "a :: 1"),
            IncomingFile("F/Gamma.txt", "g :: 1\nh :: 2"),
            IncomingFile("F/Sub/Delta.txt", "d :: 1"),
            IncomingFile("Other.txt", "o :: 1"),
        )).first
        val f = l.folders.first { it.name == "F" }
        check("A–Z order", l.orderedDecks(f.id, manual = false).map { it.name } == listOf("Alpha", "Beta", "Gamma"))
        check("manual starts in import order", l.orderedDecks(f.id, manual = true).map { it.name } == listOf("Beta", "Alpha", "Gamma"))
        val gamma = l.decks.first { it.name == "Gamma" }
        l = l.shift(f.id, gamma.id, -1)
        check("shift up", l.orderedDecks(f.id, manual = true).map { it.name } == listOf("Beta", "Gamma", "Alpha"))
        l = l.shift(f.id, gamma.id, -1).shift(f.id, gamma.id, -1)
        check("shift stops at top", l.orderedDecks(f.id, manual = true).map { it.name } == listOf("Gamma", "Beta", "Alpha"))
        check("positions survive save/load", LibraryCodec.decode(LibraryCodec.encode(l)) == l)
        val (l2, d) = l.addDeck(f.id, "Zeta")
        check("new deck goes last", l2.orderedDecks(f.id, manual = true).last().id == d.id)
        // study progress, then reset the whole folder (incl. sub-folder) but not other decks
        val now = 1_700_000_000_000L
        l = l.cards.fold(l) { acc, c -> acc.setReview(c.id, Fsrs().next(c.review, Rating.GOOD, now)) }
        l = l.resetFolder(f.id)
        val inF = l.decksUnder(f.id).map { it.id }.toSet()
        check("folder reset clears its decks", l.cards.filter { it.deckId in inF }.all { it.review.isNew } && inF.size == 4)
        check("folder reset leaves others", l.cards.filter { it.deckId !in inF }.none { it.review.isNew })
        check("no new-card cap", l.studyQueue(inF, now, Int.MAX_VALUE).size == 5)
    }

    println("Search scope / single exports")
    run {
        val l = LibraryData().importFiles(listOf(
            IncomingFile("Law/Crim/Theft.txt", "larceny :: taking\nrobbery :: larceny plus force"),
            IncomingFile("Law/Larceny notes.txt", "x :: y"),
            IncomingFile("Other.txt", "o :: 1"),
        )).first
        check("default scope finds front+back+deck", l.search("larceny").let { it.cards.size == 2 && it.decks.size == 1 })
        check("fronts only", l.search("larceny", SearchScope(backs = false, decks = false, folders = false)).cards.map { it.front } == listOf("larceny"))
        check("backs only", l.search("larceny", SearchScope(fronts = false, decks = false, folders = false)).cards.map { it.front } == listOf("robbery"))
        check("decks only", l.search("larceny", SearchScope(fronts = false, backs = false, folders = false)).let { it.cards.isEmpty() && it.decks.size == 1 })
        check("folders only", l.search("crim", SearchScope(fronts = false, backs = false, decks = false)).folders.map { it.name } == listOf("Crim"))
        val law = l.folders.first { it.name == "Law" }
        val zip = l.exportFolderZip(law.id)
        val names = java.util.zip.ZipInputStream(zip.inputStream()).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
        check("folder zip starts at the folder", names.toSet() == setOf("Law/Crim/Theft.txt", "Law/Larceny notes.txt"), names)
        val crim = l.folders.first { it.name == "Crim" }
        val names2 = java.util.zip.ZipInputStream(l.exportFolderZip(crim.id).inputStream()).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
        check("nested folder zip", names2 == listOf("Crim/Theft.txt"), names2)
        check("file name cleaned", exportFileName("A/B: c?", "txt") == "A-B- c-.txt", exportFileName("A/B: c?", "txt"))
    }

    println("Practice filters")
    run {
        var l = LibraryData().importFiles(listOf(IncomingFile("D.txt", "a :: 1\nb :: 2\nc :: 3\nd :: 4"))).first
        val now = 1_700_000_000_000L
        val (a, b, c) = l.cards
        l = l.setReview(a.id, ReviewState(due = now - 1, reps = 1, stability = 1.0, lastReview = now - DAY_MS))  // due
        l = l.setReview(b.id, ReviewState(due = now + 9 * DAY_MS, reps = 1, stability = 5.0, lastReview = now)) // learned, not due
        l = l.toggleStar(b.id)
        val ids = l.decks.map { it.id }.toSet()
        fun f(vararg x: PracticeFilter) = l.practiceCards(ids, x.toSet(), now).map { it.front }.sorted()
        check("all", f() == listOf("a", "b", "c", "d"))
        check("due", f(PracticeFilter.DUE) == listOf("a"))
        check("new", f(PracticeFilter.NEW) == listOf("c", "d"))
        check("starred", f(PracticeFilter.STARRED) == listOf("b"))
        check("due + starred", f(PracticeFilter.DUE, PracticeFilter.STARRED) == listOf("a", "b"))
        check("due + new + starred", f(PracticeFilter.DUE, PracticeFilter.NEW, PracticeFilter.STARRED) == listOf("a", "b", "c", "d"))
    }

    println("FSRS")
    val s0 = ReviewState.NEW
    for (rt in Rating.values()) println("     new card ${rt}: ${fsrs.previewLabel(s0, rt, now)}")
    val good1 = fsrs.next(s0, Rating.GOOD, now)
    val good2 = fsrs.next(good1, Rating.GOOD, good1.due)
    val again2 = fsrs.next(good1, Rating.AGAIN, good1.due)
    println("     after GOOD, then GOOD at due: ${Fsrs.formatInterval(good2.due - good1.due)}; AGAIN -> S=${"%.2f".format(again2.stability)} lapses=${again2.lapses}")
    check("intervals grow", good2.due - good1.due > good1.due - now)
    check("again lowers stability", again2.stability < good1.stability && again2.lapses == 1)
    check("again due in 10m", again2.due - good1.due == 10 * MINUTE_MS)
    val easyNew = fsrs.next(s0, Rating.EASY, now); val hardNew = fsrs.next(s0, Rating.HARD, now)
    check("easy > good > hard", easyNew.due > good1.due && good1.due > hardNew.due)
    check("format", listOf(5*MINUTE_MS, 3*DAY_MS, 21*DAY_MS, 90*DAY_MS, 400*DAY_MS).map { Fsrs.formatInterval(it) } == listOf("5m","3d","3w","3mo","1.1y"))

    println("Queue")
    val ids = lib2.decks.map { it.id }.toSet()
    val c = lib2.counts(ids, now + 10 * DAY_MS)
    check("counts: 1 due, rest new", c.due == 1 && c.new == c.total - 1, c)
    val q = lib2.studyQueue(ids, now + 10 * DAY_MS, newLimit = 3)
    check("queue due-first + new limit", q.size == 4 && q.first().front == "miranda", q.map { it.front })

    println("Codec")
    val json = LibraryCodec.encode(lib2)
    val decoded = LibraryCodec.decode(json)
    check("library round-trips", decoded == lib2)
    val weird = LibraryData(cards = listOf(Card("a", "d", "quote \" back\\slash ⏎\n\ttab é 😀", "x\u0001y")))
    check("escapes round-trip", LibraryCodec.decode(LibraryCodec.encode(weird)) == weird)

    println("Zip")
    val zip = lib2.exportZip()
    val names = java.util.zip.ZipInputStream(zip.inputStream()).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
    check("zip entries", names.toSet() == setOf("Cards/Law/Evidence/Hearsay.txt", "Cards/Law/Crim Pro.txt", "Cards/Spanish.txt"), names)


    println(if (failures == 0) "\nALL PASSED" else "\n$failures FAILED")
}

/** Runs with ./gradlew test (or the green arrow in Android Studio). */
class CoreLogicTest {
    @Test
    fun parserMergeSchedulerAndStorage() {
        failures = 0
        runChecks()
        assertEquals("checks failed - see output above", 0, failures)
    }
}
