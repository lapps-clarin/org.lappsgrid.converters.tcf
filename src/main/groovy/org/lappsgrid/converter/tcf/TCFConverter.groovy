/*
 * Copyright (c) 2017 The Language Applications Grid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package org.lappsgrid.converter.tcf

import eu.clarin.weblicht.wlfxb.io.WLDObjector
import eu.clarin.weblicht.wlfxb.io.WLFormatException
import eu.clarin.weblicht.wlfxb.tc.api.*
import eu.clarin.weblicht.wlfxb.tc.xb.LemmasLayerStored
import eu.clarin.weblicht.wlfxb.tc.xb.PosTagsLayerStored
import eu.clarin.weblicht.wlfxb.tc.xb.SentencesLayerStored
import eu.clarin.weblicht.wlfxb.tc.xb.TokensLayerStored
import eu.clarin.weblicht.wlfxb.xb.WLData
import groovy.util.logging.Slf4j
import org.lappsgrid.discriminator.Discriminators.Uri
import org.lappsgrid.serialization.Data
import org.lappsgrid.serialization.lif.Annotation
import org.lappsgrid.serialization.lif.Container
import org.lappsgrid.serialization.lif.View
import org.lappsgrid.vocabulary.Features

/**
 * @author Keith Suderman
 * @author Keigh Rim
 */
@Slf4j('logger')
class TCFConverter {

    Map getterMap = [:]
    Map<String, Offsets> offsets

    // FIXME This will not be needed once Weblicht supports view ID references.
    List<Annotation> tokens

    Container container

    String producer = this.class.getName()

    TCFConverter() {
        getterMap[TokensLayerStored] = { layer,n -> layer.getToken(n) }
        getterMap[SentencesLayerStored] = { layer,n -> layer.getSentence(n) }
        getterMap[LemmasLayerStored] = { layer,n -> layer.getLemma(n) }
        getterMap[PosTagsLayerStored] = { layer,n ->layer.getTag(n) }

        offsets = new HashMap<String,Offsets>()
        tokens = []
    }

    Data convert(String path) {
        return convert(new File(path))
    }

    Data convert(File file) {
        return convert(file.newReader())
    }

    Data convertString(String tcf) {
        logger.debug("Converting String: {}", tcf)
        return convert(new StringReader(tcf))
    }

    Data convert(Reader stream) {
        WLDObjector objector = new WLDObjector()
        WLData data
        try {
            data = objector.read(stream)
        }
        catch (WLFormatException e)
        {
            logger.error("Unable to read Weblicht document.", e)
            return new Data(Uri.ERROR, "Unable to read Weblicht document from the Reader object.")
        }
        TextCorpus corpus = data.textCorpus
        TextLayer textLayer = corpus.textLayer
        if (!textLayer) {
            //throw new ConversionException("No text layer in TCF document.")
            return new Data(Uri.ERROR, "No text layer in TCF document.")
        }

        container = new Container()
        container.text = textLayer.getText()
        container.language = corpus.getLanguage()
        processTokens(corpus)
        processSentences(corpus)
        processLemma(corpus)
        processPos(corpus)
        processMorphology(corpus)
        processNE(corpus)
        processConstituents(corpus)
        processDependencies(corpus)

        return new Data(Uri.LIF, container)
    }

    void processTokens(TextCorpus corpus) {
        TokensLayer layer = corpus.getTokensLayer()
        if (layer == null) {
            return
        }

        View view = container.newView('token-view')
        view.addContains(Uri.TOKEN, producer, "token:fromTCF")
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
            tokens.add(annotation)
            offsets[token.ID] = new Offsets(start, end)
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
            Offsets offset = offsets.get(t.ID)
            if (!offset) {
                throw new ConversionException("No such token ${t.ID} in sentence $i")
            }
            annotation.start = offset.start
            t = sentenceTokens[-1]
            offset = offsets.get(t.ID)
            if (!offset) {
                throw new ConversionException("No such token ${t.ID} in sentence $i")
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
        // wait for Serialization library v2.5.0
//        tokenView.getContains(Uri.POS).addMetadata("posTagSet", posLayer.getTagset())
        for (int i = 0; i < posLayer.size(); i++) {
            PosTag pos = posLayer.getTag(i)
            String posTag = pos.getString()
            Token[] toks = posLayer.getTokens(pos)
            Annotation token = tokenView.findById(toks[toks.length - 1].ID)
            token.addFeature(Features.Token.PART_OF_SPEECH, posTag)
        }
    }

    void processMorphology(TextCorpus corpus) {
        MorphologyLayer layer = corpus.getMorphologyLayer()
        if (layer == null) {
            return
        }

        List<View> views = container.findViewsThatContain(Uri.TOKEN)
        View view = views[-1]
        view.addContains('http://vocab.lappsgrid.org/Token#morphology', this.class.name, 'morph')

        for (int i = 0; i < layer.size(); ++i) {
            MorphologyAnalysis analysis = layer.getAnalysis(i)
            Token[] tokens = layer.getTokens(analysis)
            Annotation token = view.findById(tokens[-1].ID)
            Map fs = processFeatures(analysis.features)
            token.addFeature("morphology", fs)
        }
    }

    Map processFeatures(Feature[] features) {
        Map map = [:]
        features.each { Feature f ->
            if (f.terminal) {
                map[f.name] = f.value
            }
            else {
                map[f.name] = processFeatures(f.subfeatures)
            }

        }
        return map
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
                int curStart = it.start?: offsets[it.ID].start
                int curEnd = it.end?: offsets[it.ID].end
                start = Math.min(curStart, start)
                end = Math.max(curEnd, end)
            }
            Annotation ne = view.newAnnotation("ne_" + (i+1), Uri.NE, start, end)
            ne.addFeature(Features.NamedEntity.CATEGORY, namedEntity.getType())
        }
    }

    void processConstituents(TextCorpus corpus) {
        ConstituentParsingLayer parseLayer = corpus.getConstituentParsingLayer()
        if (!parseLayer) return

        // needed to get char offsets of sentences
        List<Annotation> sentences = container.findViewsThatContain(Uri.SENTENCE)[-1].getAnnotations();

        View constituentView = (View) container.newView('constituent-view')
        constituentView.addContains(Uri.PHRASE_STRUCTURE, producer, "phrase_structure:fromTCF")

        // FIXME Copying tokens to the view is a hack-around until Weblicht supports view ID references.
        constituentView.addContains(Uri.TOKEN, producer, "token")
        constituentView.annotations.addAll(tokens)

        // wait for Serialization library v2.5.0
        constituentView.getContains(Uri.PHRASE_STRUCTURE).put("categorySet", parseLayer.getTagset())
        constituentView.addContains(Uri.CONSTITUENT, producer, "constituent:fromTCF")
//        constituentView.dependsOn("token-view")

        // for each parse (sentence), create a new annotation of PS
        for (int sentId = 0; sentId < parseLayer.size(); sentId++) {
            ConstituentParse parse = parseLayer.getParse(sentId);
            Annotation sentence = sentences[sentId];
            Constituent root = parse.getRoot()
            int constId = 0
            Queue<Filiation> queue = new LinkedList<>();

            // IMPORTANT: we have to generate constituent IDs, since the weblicht
            // library does not provide a getter for them. However, I process
            // the tree top-down, giving the first ID the root, and incrementally
            // to the children, which is not true in most weblicht tools when they
            // generate TCF file. The example on the weblicht wiki particularly
            // shows such a tool gives ID in a bottom-up passion, so the root gets
            // the last ID. Thus, through our conversion, it is almost always the
            // case that we cannot recover the original IDs from TCF input.

            String curNodeId = "c_${sentId}_${constId++}"
            queue.add(new Filiation(root, curNodeId, "null"))

            List<String> constituentIds = new LinkedList<>()

            // now use the queue to add each node as a new CONSTITUENT annotation
            while (!queue.isEmpty()) {
                Filiation curNode = queue.removeFirst()
                curNodeId = curNode.nodeId
                constituentIds.add(curNodeId)

                Annotation constituent = constituentView.newAnnotation(curNodeId, Uri.CONSTITUENT)
                constituent.label = curNode.node.getCategory()
                constituent.addFeature(Features.Constituent.PARENT, curNode.parent)
                List<String> childrenIDs
                if (curNode.node.isTerminal()) {
                    // FIXME Why a class not found for the closure?
//                    childrenIDs = parseLayer.getTokens(curNode.getNode()).collect({"token-view:${it.getID()}"})
                    // FIXME See hack-around above.
                    childrenIDs = parseLayer.getTokens(curNode.getNode()).collect { it.getID() }
                } else {
                    childrenIDs = new LinkedList<>()
                    for (Constituent node : curNode.node.getChildren()) {
                        String childNodeId = "c_${sentId}_${constId++}"
                        childrenIDs.add(childNodeId)
                        queue.add(new Filiation(node, childNodeId, curNodeId))
                    }
                }
                constituent.addFeature(Features.Constituent.CHILDREN, childrenIDs)
            }
            // FIXME See above
//            constituentIds.addAll(parseLayer.getTokens(root).collect({"token-view:${it.getID()}"}))
//            constituentIds.addAll(parseLayer.getTokens(root).collect{ it.getID() })

            // and then add a "phrase structure" annotation for the current sentence
            Annotation phraseStructure = constituentView.newAnnotation(
                    "ps_" + (sentId), Uri.PHRASE_STRUCTURE, sentence.getStart(), sentence.getEnd());
            phraseStructure.addFeature(Features.PhraseStructure.CONSTITUENTS, constituentIds)
            phraseStructure.setLabel()
        }
    }

    void processDependencies(TextCorpus corpus) {
        DependencyParsingLayer parseLayer = corpus.getDependencyParsingLayer()
        if (!parseLayer) return

        // needed to get char offsets of sentences
        List<Annotation> sentences = container.findViewsThatContain(Uri.SENTENCE)[-1].getAnnotations();

        View dependencyView = (View) container.newView('dependency-view')
        dependencyView.addContains(Uri.DEPENDENCY_STRUCTURE, producer, "dependency_structure:fromTCF")
        // FIXME Copying tokens to the view is a hack-around until Weblicht support views IDs.
        dependencyView.addContains(Uri.TOKEN, producer, "token")
        dependencyView.annotations.addAll(tokens)

        // wait for Serialization library v2.5.0
//        dependencyView.getContains(Uri.DEPENDENCY_STRUCTURE).addMetadata("dependencySet", parseLayer.getTagset())
        dependencyView.getContains(Uri.DEPENDENCY_STRUCTURE).put("dependencySet", parseLayer.getTagset())
        dependencyView.addContains(Uri.DEPENDENCY, producer, "dependency:fromTCF")
//        dependencyView.dependsOn("token-view")

        // for each parse (sentence), create a new annotation of PS
        for (int sentId = 0; sentId < parseLayer.size(); sentId++) {
            DependencyParse parse = parseLayer.getParse(sentId)
            Annotation sentence = sentences[sentId];
            int depId = 0
            List<String> dependencyIds = new LinkedList<>()
            for (Dependency dep : parse.getDependencies()) {
                String dependencyId = "dep_${sentId}_${depId++}"
                Annotation dependency = dependencyView.newAnnotation(dependencyId, Uri.DEPENDENCY)
                dependency.setLabel(dep.getFunction())
                for (Token dependent : parseLayer.getDependentTokens(dep)) {
//                    dependency.addFeature(Features.Dependency.DEPENDENT, "token-view:${dependent.getID()}")
                    dependency.addFeature(Features.Dependency.DEPENDENT, dependent.getID())
                }
                for (Token governor : parseLayer.getGovernorTokens(dep)) {
//                    dependency.addFeature(Features.Dependency.GOVERNOR, "token-view:${governor.getID()}")
                    dependency.addFeature(Features.Dependency.GOVERNOR, governor.getID())
                }
                dependencyIds.add(dependencyId)
            }
            dependencyView.newAnnotation("depstr_${sentId}", Uri.DEPENDENCY_STRUCTURE, sentence.getStart(), sentence.getEnd()).
                    addFeature(Features.DependencyStructure.DEPENDENCIES, dependencyIds)
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
