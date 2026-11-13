package com.lostf1sh.pixelplayeross.desktop.library

import com.lostf1sh.pixelplayeross.desktop.metadata.MetadataChecks
import java.nio.file.Path
import kotlin.test.Test

class MetadataTest {
    @Test fun extractsTagsAndArtworkWithFallbacks() {
        MetadataChecks.runChecks(Path.of("src/test/resources/metadata"))
    }
}
