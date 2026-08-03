package com.musicapp.player.data.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BackupRulesContractTest {
    @Test
    fun legacyBackupRulesOnlyIncludeDataStoreFiles() {
        val includes = includesFrom("src/main/res/xml/backup_rules.xml")

        assertEquals(listOf(BackupInclude(domain = "file", path = "datastore/")), includes)
        assertNoDatabaseInclude(includes)
    }

    @Test
    fun dataExtractionRulesOnlyIncludeDataStoreFiles() {
        val includes = includesFrom("src/main/res/xml/data_extraction_rules.xml")

        assertEquals(
            listOf(
                BackupInclude(domain = "file", path = "datastore/"),
                BackupInclude(domain = "file", path = "datastore/"),
            ),
            includes,
        )
        assertNoDatabaseInclude(includes)
    }

    private fun includesFrom(relativePath: String): List<BackupInclude> {
        val file = File(relativePath)
        assertTrue("Backup rules file must exist: $relativePath", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val includes = document.getElementsByTagName("include")
        return (0 until includes.length).map { index ->
            val element = includes.item(index) as Element
            BackupInclude(
                domain = element.getAttribute("domain"),
                path = element.getAttribute("path"),
            )
        }
    }

    private fun assertNoDatabaseInclude(includes: List<BackupInclude>) {
        assertFalse(includes.any { include -> include.domain.contains("database", ignoreCase = true) })
        assertFalse(includes.any { include -> include.path.contains("database", ignoreCase = true) })
    }

    private data class BackupInclude(
        val domain: String,
        val path: String,
    )
}
