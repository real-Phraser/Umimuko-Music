package com.lostf1sh.pixelplayeross.desktop

import com.lostf1sh.pixelplayeross.desktop.library.*
import com.lostf1sh.pixelplayeross.desktop.playback.EditableQueue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

fun main() {
    com.lostf1sh.pixelplayeross.desktop.playback.SmtcDelegateChecks.run()
    queueChecks()
    Files.createDirectories(Path.of("build/model-checks"))
    val directory=Files.createTempDirectory(Path.of("build/model-checks"),"run-")
    try {homeChecks(directory);libraryChecks(directory);sectionChecks()}
    finally {Files.walk(directory).use{paths->paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)}}
    println("PASS: editable queue, duplicate entries, shuffle, home persistence, resume, pins, album ordering, playlist compatibility.")
}

private fun queueChecks() {
    val queue=EditableQueue<String>()
    queue.replace(listOf("A","B","A","C"),2,false)
    val selected=queue.entries()[2].key();val otherA=queue.entries()[0].key()
    queue.moveTo(otherA,3)
    check(queue.entries()[queue.index()].key()==selected)
    check(!queue.remove(setOf(otherA)))
    check(queue.current()=="A" && queue.size()==3)
    queue.toggleShuffle()
    check(queue.current()=="A" && queue.entries().first().key()==selected)
    queue.insertNext("Next")
    check(queue.entries()[queue.index()+1].value()=="Next")
    queue.toggleShuffle()
    check(queue.current()=="A")
    queue.moveBy(selected,1)
    val next=queue.entries().getOrNull(queue.index()+1)?.key()
    val previous=queue.entries().getOrNull(queue.index()-1)?.key()
    check(queue.remove(setOf(selected)))
    check(queue.entries()[queue.index()].key()==(next?:previous))
    queue.clearPlayed();check(queue.index()==0)
    queue.remove(queue.entries().map{it.key()}.toSet())
    check(queue.index()==-1 && queue.current()==null)
    queue.insertNext("Restart");check(queue.current()=="Restart")
    check(queue.step(1,true) && queue.index()==0)
    queue.replace(List(12){"Track ${it%3}"},5,false)
    val keys=queue.entries().map{it.key()}.toSet();val current=queue.entries()[queue.index()].key()
    val random=Random(42)
    repeat(500) {
        queue.moveTo(queue.entries()[random.nextInt(queue.size())].key(),random.nextInt(queue.size()))
        check(queue.entries()[queue.index()].key()==current)
        check(queue.entries().map{it.key()}.toSet()==keys)
    }
    queue.toggleShuffle();check(queue.entries().map{it.key()}.toSet()==keys)
    queue.toggleShuffle();check(queue.entries()[queue.index()].key()==current)
}

private fun homeChecks(directory:Path) {
    val store=HomeStore(directory.resolve("home"));store.load()
    val a="C:\\موسیقی\\A.flac";val b="C:\\Music\\B.flac"
    store.registerLibrary(listOf(a),100)
    store.registerLibrary(listOf(a,b),200)
    check(store.snapshot().added()[a]==100L && store.snapshot().added()[b]==200L)
    store.played(a);store.played(b);store.played(a)
    check(store.snapshot().recent()==listOf(a,b))
    val album="Album\u0000Artist"
    store.togglePin("album",album);store.togglePin("playlist","list-1");store.movePin("playlist","list-1",-1)
    check(store.snapshot().pins().first()==HomeStore.Pin("playlist","list-1"))
    store.saveSession(listOf(a,b,a),2,12345)
    val queueFile=directory.resolve("home/queue.properties");val queueBytes=Files.readAllBytes(queueFile)
    store.saveSession(listOf(a,b,a),2,15000)
    check(Files.readAllBytes(queueFile).contentEquals(queueBytes)) // clock ticks do not rewrite the queue
    val restored=HomeStore(directory.resolve("home")).load()
    check(restored.session().paths()==listOf(a,b,a) && restored.session().index()==2 && restored.session().time()==15000L)
    check(restored.pins()==store.snapshot().pins() && restored.recent()==listOf(a,b))
    val track=DesktopSong(a,"A",a,"FLAC",1)
    val resume=resolveSession(restored.session(),mapOf(a to track))!!
    check(resume.tracks.size==2 && resume.index==1 && resume.time==15000L)
    val missingCurrent=resolveSession(HomeStore.Session(listOf(b,a),0,9999),mapOf(a to track))!!
    check(missingCurrent.index==0 && missingCurrent.time==0L)
    store.saveSession(emptyList(),-1,0)
    check(HomeStore(directory.resolve("home")).load().session().paths().isEmpty())
    val homeFile=directory.resolve("home/home.properties")
    Files.writeString(homeFile,"count=invalid\n")
    val broken=HomeStore(directory.resolve("home"))
    check(runCatching{broken.load()}.isFailure)
    check(runCatching{broken.togglePin("album","x")}.isFailure)
    check(Files.readString(homeFile)=="count=invalid\n")
}

private fun libraryChecks(directory:Path) {
    val file=directory.resolve("old-library.properties");val store=UserLibraryStore(file)
    store.load();store.toggleLike("liked-track");store.create("Existing")
    store.createFromTracks("Saved queue",listOf("A","B","A"))
    val restored=UserLibraryStore(file).load()
    check(restored.isLiked("liked-track") && restored.playlists.size==2)
    check(restored.playlists.last().paths==listOf("A","B"))
}

private fun sectionChecks() {
    val a=DesktopSong("a","Song A","C:/Music/A/1.flac","FLAC",1,artist="Artist A",album="Album A",albumArtist="Artist A")
    val b=DesktopSong("b","Song B","C:/Music/B/1.flac","FLAC",1,artist="Artist B",album="Album B",albumArtist="Artist B")
    val albums=LibraryIndex.albums(listOf(a,b))
    val albumA=albums.first{it.title=="Album A"};val albumB=albums.first{it.title=="Album B"}
    val list=MusicGroup("list-1","Evening","1 track",listOf(a))
    val data=HomeStore.Snapshot(mapOf("a" to 100L,"b" to 200L),listOf("b","a","b"),
        listOf(HomeStore.Pin("album",albumA.id),HomeStore.Pin("playlist","list-1")),HomeStore.Session(emptyList(),-1,0))
    val sections=homeSections(albums,listOf(list),data,"")
    check(sections.pinned.map{it.type}==listOf("album","playlist"))
    check(sections.added.first().group.id==albumB.id)
    check(sections.recent.map{it.group.id}==listOf(albumB.id,albumA.id))
    check(homeSections(albums,listOf(list),data,"Artist A").added.map{it.group.id}==listOf(albumA.id))
}
