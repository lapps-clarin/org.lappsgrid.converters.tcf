package org.lappsgrid.converter.tcf

import eu.clarin.weblicht.wlfxb.io.WLDObjector
import eu.clarin.weblicht.wlfxb.tc.api.TextCorpus
import eu.clarin.weblicht.wlfxb.tc.api.TextLayer
import eu.clarin.weblicht.wlfxb.xb.WLData
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.lappsgrid.discriminator.Discriminators
import org.lappsgrid.serialization.lif.Container
import org.lappsgrid.serialization.lif.View


/**
 * @author Keith Suderman
 */
class TCFConverterTests {

    Container container

    @Before
    void setup() {
        InputStream stream = this.class.classLoader.getResourceAsStream("/karen-flew.xml")
        if (!stream) {
            throw new IOException("Unable to load test file.")
        }
        WLDObjector objector = new WLDObjector()
        WLData data = objector.read(stream)

        TextCorpus corpus = data.textCorpus
        TextLayer textLayer = corpus.textLayer
        if (!textLayer) {
            throw new ConversionException("No text layer in TCF document.")
        }

        container = new Container()
        container.text = textLayer.getText()
        container.language = corpus.getLanguage()
    }

    @After
    void teardown() {
        container = null
    }

    @Test
    void testTokens() {
        List<View> views = container.findViewsThatContain(Discriminators.Uri.TOKEN)
        assertTrue 1 == views.size()
        assertTrue 6 == views[0].annotations.size()

    }

    @Test
    void testLemmas() {

    }

    @Test
    void testSentences() {

    }
}
