package org.lappsgrid.converter.tcf

//import org.lappsgrid.serialization.lif.*

import eu.clarin.weblicht.wlfxb.io.TextCorpusStreamed
import eu.clarin.weblicht.wlfxb.io.WLDObjector
import eu.clarin.weblicht.wlfxb.tc.api.Lemma
import eu.clarin.weblicht.wlfxb.tc.api.LemmasLayer
import eu.clarin.weblicht.wlfxb.tc.api.SentencesLayer
import eu.clarin.weblicht.wlfxb.tc.api.TextLayer
import eu.clarin.weblicht.wlfxb.tc.api.Token
import eu.clarin.weblicht.wlfxb.tc.xb.PosTagsLayerStored
import eu.clarin.weblicht.wlfxb.tc.api.TextCorpus
import eu.clarin.weblicht.wlfxb.tc.api.TextCorpusLayer
import eu.clarin.weblicht.wlfxb.tc.api.TokensLayer
import eu.clarin.weblicht.wlfxb.tc.xb.LemmasLayerStored
import eu.clarin.weblicht.wlfxb.tc.xb.SentencesLayerStored
import eu.clarin.weblicht.wlfxb.tc.xb.TextCorpusLayerTag
import eu.clarin.weblicht.wlfxb.tc.xb.TokensLayerStored
import eu.clarin.weblicht.wlfxb.xb.WLData
import groovyjarjarantlr.TokenStreamRewriteEngine
import org.lappsgrid.discriminator.Discriminators.Uri
import org.lappsgrid.serialization.DataContainer
import org.lappsgrid.serialization.lif.Annotation
import org.lappsgrid.serialization.lif.Container
import org.lappsgrid.serialization.lif.View
import sun.security.util.Length

/**
 * @author Keith Suderman
 */
class TCFConverter {

    Map getterMap = [:]
    Map<String, Offsets> tokens = new HashMap<String,Offsets>()
    Container container

    String producer = this.class.getName()

    TCFConverter() {
        getterMap[TokensLayerStored] = { layer,n -> layer.getToken(n) }
        getterMap[SentencesLayerStored] = { layer,n -> layer.getSentence(n) }
        getterMap[LemmasLayerStored] = { layer,n -> layer.getLemma(n) }
        getterMap[PosTagsLayerStored] = { layer,n ->layer.getTag(n) }
    }


    String load(String path) {
        InputStream stream = this.class.getResourceAsStream(path)
        if (stream == null) {
            throw new IOException("Unable to load file {}", path)
        }
        return stream.text
    }

    void parse() {
        XmlParser parser = new XmlParser(false, true)
        InputStream stream = this.class.getResourceAsStream('/karen-flew.xml')
        if (!stream) {
            throw new IOException("Unable to load test file.")
        }
        def xml = parser.parse(stream)
        xml.children().each { println it }
    }

    void processTokens(TextCorpus corpus) {
        View view = container.newView('token-view')
        view.addContains(Uri.TOKEN, producer, "token:fromTCF")
        TokensLayer layer = corpus.getTokensLayer()
        String text = container.text
        int n = layer.size()
        int start = 0
        for (int i = 0; i < n; ++i) {
            Token token = layer.getToken(i)
            Annotation annotation = view.newAnnotation()
            annotation.id = token.ID
            annotation.features.word = token.string
            annotation.atType = Uri.TOKEN
            start = text.indexOf(token.string, start)
            if (start < 0) {
                throw new ConversionException("Unable to match string \"${token.string}\" in the text")
            }
            int end = start + token.string.length()
            annotation.start = start
            annotation.end = end
            tokens[token.ID] = new Offsets(start, end)
        }
    }

    void processSentences(TextCorpus corpus) {
        SentencesLayer layer = corpus.getSentencesLayer()
        if (!layer) {
            return
        }
        View view = container.newView('sentence-view')
    }

    void processLemma(TextCorpus corpus) {
        LemmasLayer lemmasLayer = corpus.getLemmasLayer()
        if (!lemmasLayer) {
            return
        }
        View tokenView = (View) container.getView(0)
        tokenView.addContains(Uri.LEMMA, producer, "lemma:fromTCF")
        for (int i = 0; i < lemmasLayer.size(); i++) {
            Lemma lemma = lemmasLayer.getLemma(i)
            String lemmaString = lemma.getString()
            Token[] toks = lemmasLayer.getTokens(lemma)
            println "Found ${toks.length} tokens from the lemma ${lemmaString}"
            Annotation token = tokenView.findById(toks[toks.length - 1].ID)
            token.addFeature(Uri.LEMMA, lemmaString)
        }
        container.addView(tokenView)

    }

    void run() {
        InputStream stream = this.class.getResourceAsStream('/karen-flew.xml')
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
        processTokens(corpus)
        processLemma(corpus)

        DataContainer dc = new DataContainer(container)
        println dc.asPrettyJson()
        /*
        corpus.layers.each { TextCorpusLayer layer ->
            if (layer instanceof PosTagsLayerStored) {
                println "Tag layer"
            }
            else {
                println "${layer.class.name} is not a ${PosTagsLayerStored.class.name}"
            }
            println layer.class.name
            def getter = getterMap[layer.class]
            if (getter) {
                try {
                    int i = 0
                    def object = getter(layer, i++)
                    while (object) {
                        println object
                        object = getter(layer, i++)
                    }
                }
                catch(IndexOutOfBoundsException e) {
                    // Ignored as this is expected when we reach the end of the list
                }
                catch (Exception e) {
                    // This is not expected.
                    throw e
                }
            }
            else {
                println "No getter found for ${layer.class.simpleName}"
                println layer.class.classLoader.class.name
                getterMap.each { k,v ->
                    if (layer instanceof PosTagsLayerStored) {
                        println "Found"
                    }
                }
            }
        }
        */
    }
    static void main(String[] args) {
        new TCFConverter().run()
    }
}
