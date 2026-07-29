package com.musicapp.player.core.designsystem

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class StringResourceParityTest {
  @Test
  fun defaultAndSimplifiedChineseStringKeysMatch() {
    val resourceRoot = locateResourceRoot()
    val defaultKeys = parseKeys(File(resourceRoot, "values"))
    val simplifiedChineseKeys = parseKeys(File(resourceRoot, "values-zh-rCN"))

    assertEquals(defaultKeys, simplifiedChineseKeys)
  }

  private fun parseKeys(directory: File): Set<ResourceKey> {
    val files =
      directory.listFiles { file -> file.isFile && file.extension == "xml" }?.sortedBy(File::getName)
        ?: error("Cannot read resource directory ${directory.absolutePath}")
    return files.flatMapTo(mutableSetOf()) { parseFileKeys(it) }
  }

  private fun parseFileKeys(file: File): Set<ResourceKey> {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    val document = factory.newDocumentBuilder().parse(file)
    val children = document.documentElement.childNodes
    return buildSet {
      for (index in 0 until children.length) {
        val element = children.item(index) as? Element ?: continue
        if (element.tagName == "string" || element.tagName == "plurals") {
          add(ResourceKey(type = element.tagName, name = element.getAttribute("name")))
        }
      }
    }
  }

  private fun locateResourceRoot(): File {
    val candidates =
      listOf(
        File("src/main/res"),
        File("app/src/main/res"),
      )
    return candidates.firstOrNull(File::isDirectory)
      ?: error("Cannot locate app/src/main/res from ${File(".").absolutePath}")
  }

  private data class ResourceKey(val type: String, val name: String)
}
