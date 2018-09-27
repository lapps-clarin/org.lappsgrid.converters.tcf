package org.lappsgrid.converter.tcf

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.lappsgrid.serialization.Data
import org.lappsgrid.serialization.lif.Annotation
import org.lappsgrid.serialization.lif.Container
import org.lappsgrid.serialization.lif.View
import org.lappsgrid.vocabulary.Features

import static org.junit.Assert.assertThat
import static org.lappsgrid.discriminator.Discriminators.*

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * @author Keith Suderman
 */
class TCFConverterTests {

    Data data
    Container container

    @Before
    void setup() {
        InputStream stream = this.class.classLoader.getResourceAsStream("karen-flew-full" +
                ".xml")
        assert stream != null
        data = new TCFConverter().convertString(stream.text)
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
        List<View> views = container.findViewsThatContain(Uri.TOKEN)
        // FIXME This will change when the Weblich hack-around is removed.
//        assertTrue 3 == views.size()
        assert 1 == views.size()

        assert 6 == views[0].annotations.size()

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
        List<View> views = container.findViewsThatContain(Uri.LEMMA)
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
        List<View> views = container.findViewsThatContain(Uri.SENTENCE)
        assert 1 == views.size()
        assert 1 == views[0].annotations.size()

        List<Annotation> annotations = views[0].annotations
        assertEquals 'Karen flew to New York.', extractText(annotations[0])
    }

    @Test
    void testConstituent() {
        List<View> views = container.findViewsThatContain(Uri.PHRASE_STRUCTURE)
        assert 1 == views.size()

        // FIXME after Weblicht hack-around removed.
//        List<Annotation> annotations = views[0].annotations
        List<Annotation> annotations = views[0].annotations.findAll { it.atType != Uri.TOKEN }

        assert 13 == annotations.size()

        Map<String, String> filiations = new HashMap<>()
        Map<String, String> labels = new HashMap<>()
        for (Annotation annotation : annotations) {
            if (annotation.getAtType() == Uri.PHRASE_STRUCTURE) {
                // 12 constituents and 6 tokens
                assertEquals(12, parseListString(annotation.getFeature(Features.PhraseStructure.CONSTITUENTS)).length)
            } else {
                parseListString(annotation.getFeature(Features.Constituent.CHILDREN)).each { String child ->
                    filiations.put(child.replaceAll("^.+:", ""), annotation.getId())
                }
                labels.put(annotation.getId(), annotation.getLabel())
            }
        }
        assertEquals("NNP", labels[filiations["t_0"]])
        assertEquals("NP", labels[filiations[filiations["t_0"]]])
        assertEquals("S", labels[filiations[filiations[filiations["t_0"]]]])
        assertEquals("TOP", labels[filiations[filiations[filiations[filiations["t_0"]]]]])

        assertEquals("VBD", labels[filiations["t_1"]])
        assertEquals("VP", labels[filiations[filiations["t_1"]]])
        assertEquals("S", labels[filiations[filiations[filiations["t_1"]]]])
        assertEquals("TOP", labels[filiations[filiations[filiations[filiations["t_1"]]]]])

        assertEquals("TO", labels[filiations["t_2"]])
        assertEquals("PP", labels[filiations[filiations["t_2"]]])
        assertEquals("VP", labels[filiations[filiations[filiations["t_2"]]]])
        assertEquals("S", labels[filiations[filiations[filiations[filiations["t_2"]]]]])
        assertEquals("TOP", labels[filiations[filiations[filiations[filiations[filiations["t_2"]]]]]])

        assertEquals("NNP", labels[filiations["t_3"]])
        assertEquals("NP", labels[filiations[filiations["t_3"]]])
        assertEquals("PP", labels[filiations[filiations[filiations["t_3"]]]])
        assertEquals("VP", labels[filiations[filiations[filiations[filiations["t_3"]]]]])
        assertEquals("S", labels[filiations[filiations[filiations[filiations[filiations["t_3"]]]]]])
        assertEquals("TOP", labels[filiations[filiations[filiations[filiations[filiations[filiations["t_3"]]]]]]])

        assertEquals("NNP", labels[filiations["t_4"]])
        assertEquals("NP", labels[filiations[filiations["t_4"]]])
        assertEquals("PP", labels[filiations[filiations[filiations["t_4"]]]])
        assertEquals("VP", labels[filiations[filiations[filiations[filiations["t_4"]]]]])
        assertEquals("S", labels[filiations[filiations[filiations[filiations[filiations["t_4"]]]]]])
        assertEquals("TOP", labels[filiations[filiations[filiations[filiations[filiations[filiations["t_4"]]]]]]])

        assertEquals(".", labels[filiations["t_5"]])
        assertEquals("S", labels[filiations[filiations["t_5"]]])
        assertEquals("TOP", labels[filiations[filiations[filiations["t_5"]]]])

        println data.asPrettyJson()
    }

    String[] parseListString(String string) {
        return string.substring(1, string.size() -1).split(",\\s+")
    }

    @Test
    void testDependency() {
        List<View> views = container.findViewsThatContain(Uri.DEPENDENCY_STRUCTURE)
        assert 1 == views.size()

        // FIXME after Weblicht supports view ID references
        List<Annotation> annotations = views[0].annotations.findAll { it.atType != Uri.TOKEN }
        assert 7 == annotations.size()

        for (Annotation annotation : annotations) {
            if (annotation.getAtType() == Uri.DEPENDENCY_STRUCTURE) {
                assertEquals(6, parseListString(annotation.getFeature(Features.DependencyStructure.DEPENDENCIES)).length)
            } else {
                assertEquals(Uri.DEPENDENCY, annotation.getAtType())
                switch (annotation.getFeature(Features.Dependency.DEPENDENT).replaceAll("^.+:", "")) {
                    case "t_0":
                        assertEquals("t_1", annotation.getFeature(Features.Dependency.GOVERNOR).replaceAll("^.+:", ""))
                        assertEquals("nsubj", annotation.getLabel())
                        break
                    case "t_1":
                        assertEquals(null, annotation.getFeature(Features.Dependency.GOVERNOR))
                        assertEquals("root", annotation.getLabel())
                        break
                    case "t_2":
                        assertEquals("t_1", annotation.getFeature(Features.Dependency.GOVERNOR).replaceAll("^.+:", ""))
                        assertEquals("prep", annotation.getLabel())
                        break
                    case "t_3":
                        assertEquals("t_4", annotation.getFeature(Features.Dependency.GOVERNOR).replaceAll("^.+:", ""))
                        assertEquals("nn", annotation.getLabel())
                        break
                    case "t_4":
                        assertEquals("t_2", annotation.getFeature(Features.Dependency.GOVERNOR).replaceAll("^.+:", ""))
                        assertEquals("pobj", annotation.getLabel())
                        break
                    case "t_5":
                        assertEquals("t_1", annotation.getFeature(Features.Dependency.GOVERNOR).replaceAll("^.+:", ""))
                        assertEquals("punct", annotation.getLabel())
                        break
                }
            }
        }
    }
}
