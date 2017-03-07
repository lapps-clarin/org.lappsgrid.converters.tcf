package org.lappsgrid.converter.tcf

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.lappsgrid.serialization.Data
import org.lappsgrid.serialization.lif.Annotation

import static org.junit.Assert.*

import org.lappsgrid.discriminator.Discriminators
import org.lappsgrid.serialization.lif.Container
import org.lappsgrid.serialization.lif.View


/**
 * @author Keith Suderman
 */
class TCFConverterTests {

    Data data
    Container container

    @Before
    void setup() {
        InputStream stream = this.class.classLoader.getResourceAsStream("karen-flew.xml")
        assert stream != null
        data = new TCFConverter().convert(stream)
        container = data.payload
    }

    @After
    void teardown() {
        container = null
    }

    String extractText(Annotation a) {
        return container.text.substring((int)a.start, (int)a.end)
    }

    @Test
    void testLanguageAndText() {
        assertTrue 'en' == container.language
        assertTrue 'Karen flew to New York.' == container.text
    }

    @Test
    void testTokens() {
        List<View> views = container.findViewsThatContain(Discriminators.Uri.TOKEN)
        assertTrue 1 == views.size()
        assertTrue 6 == views[0].annotations.size()

        List<Annotation> annotations = views[0].annotations
        assertEquals 'Karen', extractText(annotations[0])
        assertEquals 'flew', extractText(annotations[1])
        assertEquals 'to', extractText(annotations[2])
        assertEquals 'New', extractText(annotations[3])
        assertEquals 'York', extractText(annotations[4])
        assertEquals '.', extractText(annotations[5])
    }

    @Test
    void testLemmas() {
        List<View> views = container.findViewsThatContain(Discriminators.Uri.LEMMA)
        assertTrue 1 == views.size()
        assertTrue 6 == views[0].annotations.size()

        List<Annotation> annotations = views[0].annotations
        assertEquals 'karen', annotations[0].features.lemma
        assertEquals 'fly', annotations[1].features.lemma
        assertEquals 'to', annotations[2].features.lemma
        assertEquals 'new', annotations[3].features.lemma
        assertEquals 'york', annotations[4].features.lemma
        assertEquals '.', annotations[5].features.lemma
    }

    @Test
    void testSentences() {
        List<View> views = container.findViewsThatContain(Discriminators.Uri.SENTENCE)
        assert 1 == views.size()
        assert 1 == views[0].annotations.size()

        List<Annotation> annotations = views[0].annotations
        assertEquals 'Karen flew to New York.', extractText(annotations[0])
    }
}
