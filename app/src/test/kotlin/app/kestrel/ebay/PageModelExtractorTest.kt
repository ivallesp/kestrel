package app.kestrel.ebay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PageModelExtractorTest {
    @Test
    fun cutsBalancedObjectsIgnoringBracesInsideStrings() {
        val page =
            """x=${'$'}M_1_C.concat({"o":{"SEMANTIC_DATA":{"_type":"SemanticDataModule",""" +
                """"t":"a } { \" } b","n":{"d":1}},"after":2}})"""
        val module = PageModelExtractor.module(page, "SEMANTIC_DATA", "SemanticDataModule")!!
        assertEquals("a } { \" } b", module["t"].asText())
        assertEquals(1L, module.path("n", "d").asWholeNumber())
        assertNull(module["after"])
    }

    @Test
    fun toleratesWhitespaceAroundTheColon() {
        assertNotNull(PageModelExtractor.module("{\"BUY_BOX\" :\n {\"_type\":\"BuyBoxModule\"}}", "BUY_BOX"))
    }

    @Test
    fun prefersTheObjectWithTheExpectedType() {
        val page =
            """{"BUY_BOX":{"_type":"Region","layout":1},""" +
                """"modules":{"BUY_BOX":{"_type":"BuyBoxModule","bidPrice":{}}}}"""
        assertEquals("BuyBoxModule", PageModelExtractor.module(page, "BUY_BOX", "BuyBoxModule")!!["_type"].asText())
        assertEquals("Region", PageModelExtractor.module(page, "BUY_BOX")!!["_type"].asText())
    }

    @Test
    fun neverConfusesSimilarKeysOrEscapedCopies() {
        val page =
            """{"BUY_BOX_CTA":{"a":1},"moduleLocator":"BUY_BOX",""" +
                """"s":"{\"BUY_BOX\":{\"_type\":\"BuyBoxModule\"}}"}"""
        assertNull(PageModelExtractor.module(page, "BUY_BOX"))
    }

    @Test
    fun unbalancedOrInvalidObjectsAreSkipped() {
        assertNull(PageModelExtractor.module("""{"SEMANTIC_DATA":{"a":1""", "SEMANTIC_DATA"))
        assertNull(PageModelExtractor.module("""{"SEMANTIC_DATA":{"a":,}}""", "SEMANTIC_DATA"))
        assertNull(PageModelExtractor.module("""{"SEMANTIC_DATA":[1,2]}""", "SEMANTIC_DATA"))
        assertEquals(
            2L,
            PageModelExtractor
                .module("""{"SEMANTIC_DATA":{"a":,}} {"SEMANTIC_DATA":{"a":2}}""", "SEMANTIC_DATA")!!["a"]
                .asWholeNumber(),
        )
    }

    @Test
    fun findsTheModulesInEveryFixture() {
        for (name in Fixtures.itemPageNames()) {
            val page = Fixtures.itemPage(name)
            assertNotNull(name, PageModelExtractor.module(page, "SEMANTIC_DATA", "SemanticDataModule"))
            assertNotNull(name, PageModelExtractor.module(page, "BUY_BOX", "BuyBoxModule"))
        }
    }
}
