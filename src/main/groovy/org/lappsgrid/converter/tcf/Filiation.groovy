package org.lappsgrid.converter.tcf

import eu.clarin.weblicht.wlfxb.tc.api.Constituent

/**
 * Created by krim on 5/22/2017.
 */
class Filiation {
    Constituent node
    String nodeId
    String parent

    public Filiation(Constituent node, String nodeId, String parentId) {
        this.node = node
        this.nodeId = nodeId
        this.parent = parentId
    }

    public Filiation(String nodeId, String parentId) {
        this(null, nodeId, parentId)
    }

}
