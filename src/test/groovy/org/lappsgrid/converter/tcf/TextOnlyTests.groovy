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

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.lappsgrid.serialization.Data
import org.lappsgrid.serialization.lif.Annotation
import org.lappsgrid.serialization.lif.Container

import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertTrue

/**
 *
 */
class TextOnlyTests {

    Data data
    Container container

    @Before
    void setup() {
        InputStream stream = this.class.classLoader.getResourceAsStream("karen-flew-text.xml")
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
}
