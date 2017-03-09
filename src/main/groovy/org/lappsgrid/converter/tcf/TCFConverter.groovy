package org.lappsgrid.converter.tcf

import eu.clarin.weblicht.wlfxb.io.WLDObjector
import eu.clarin.weblicht.wlfxb.tc.api.*
import eu.clarin.weblicht.wlfxb.tc.xb.LemmasLayerStored
import eu.clarin.weblicht.wlfxb.tc.xb.PosTagsLayerStored
import eu.clarin.weblicht.wlfxb.tc.xb.SentencesLayerStored
import eu.clarin.weblicht.wlfxb.tc.xb.TokensLayerStored
import eu.clarin.weblicht.wlfxb.xb.WLData
import org.lappsgrid.discriminator.Discriminators.Uri
import org.lappsgrid.serialization.Data
import org.lappsgrid.serialization.DataContainer
import org.lappsgrid.serialization.lif.Annotation
import org.lappsgrid.serialization.lif.Container
import org.lappsgrid.serialization.lif.View
import org.lappsgrid.vocabulary.Features

/**
 * @author Keith Suderman
 * @author Keigh Rim
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

    Data convert(String path) {
        return convert(new File(path).newInputStream())
    }

    Data convert(File file) {
        return convert(file.newInputStream())
    }

    Data convertString(String tcf) {
        return convert(new InputStreamReader(new StringReader(tcf)))
    }

    Data convert(InputStream stream) {
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
        processSentences(corpus)
        processLemma(corpus)
        processPos(corpus)
        processNE(corpus)

        return new DataContainer(container)
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
        view.addContains(Uri.SENTENCE, this.class.name, "tcf:sentence")
        for (int i; i < layer.size(); ++i) {
            Sentence s = layer.getSentence(i)
            Token[] sentenceTokens = layer.getTokens(s)
            if (sentenceTokens == null || sentenceTokens.length == 0) {
                throw new ConversionException("Sentence does not contain any tokens")
            }
            Annotation annotation = view.newAnnotation("s_${i+1}", Uri.SENTENCE)
            annotation.label = "Sentence"
            Token t = sentenceTokens[0]
            Offsets offset = tokens.get(t.ID)
            if (!offset) {
                throw new ConversionException("No such token ${t.ID} in sentence $i")
            }
            annotation.start = offset.start
            t = sentenceTokens[-1]
            offset = tokens.get(t.ID)
            if (!offset) {
                throw new ConversionException("No sucj token ${t.ID} in sentence $i")
            }
            annotation.end = offset.end
        }
    }

    void processLemma(TextCorpus corpus) {
        LemmasLayer lemmasLayer = corpus.getLemmasLayer()
        if (!lemmasLayer) {
            return
        }
        List views = container.findViewsThatContain(Uri.TOKEN)
        View tokenView = views.get(views.size() - 1)
        tokenView.addContains(Uri.LEMMA, producer, "lemma:fromTCF")
        for (int i = 0; i < lemmasLayer.size(); i++) {
            Lemma lemma = lemmasLayer.getLemma(i)
            String lemmaString = lemma.getString()
            Token[] toks = lemmasLayer.getTokens(lemma)
            Annotation token = tokenView.findById(toks[toks.length - 1].ID)
            token.addFeature(Features.Token.LEMMA, lemmaString)
        }
    }

    void processPos(TextCorpus corpus) {
        PosTagsLayer posLayer = corpus.getPosTagsLayer()
        if (!posLayer) {
            return
        }
        List views = container.findViewsThatContain(Uri.TOKEN)
        View tokenView = views.get(views.size() - 1)
        tokenView.addContains(Uri.POS, producer, "pos:fromTCF")
        for (int i = 0; i < posLayer.size(); i++) {
            PosTag pos = posLayer.getTag(i)
            String posTag = pos.getString()
            Token[] toks = posLayer.getTokens(pos)
            Annotation token = tokenView.findById(toks[toks.length - 1].ID)
            token.addFeature(Features.Token.PART_OF_SPEECH, posTag)
        }
    }

    void processNE(TextCorpus corpus) {
        NamedEntitiesLayer layer = corpus.getNamedEntitiesLayer()
        if (!layer) return
        View view = container.newView("ne-view")
        view.addContains(Uri.NE, this.class.name, layer.getType())

        for (int i; i < layer.size(); ++i) {
            NamedEntity namedEntity = layer.getEntity(i)
            Token[] toks = layer.getTokens(namedEntity)
            List target = []
            int start = Double.POSITIVE_INFINITY
            int end = Double.NEGATIVE_INFINITY
            toks.each {
                target.add(it.ID)
                int curStart = it.start?: tokens[it.ID].start
                int curEnd = it.end?: tokens[it.ID].end
                start = Math.min(curStart, start)
                end = Math.max(curEnd, end)
            }
            Annotation ne = view.newAnnotation("ne_" + (i+1), Uri.NE, start, end)
            ne.addFeature(Features.NamedEntity.CATEGORY, namedEntity.getType())
        }
    }

    void run() {
        InputStream stream = this.class.getResourceAsStream('/karen-flew.xml')
        if (!stream) {
            throw new IOException("Unable to load test file.")
        }
        println convert(stream).asPrettyJson()
    }

    static void main(String[] args) {
        new TCFConverter().run()
    }
}
