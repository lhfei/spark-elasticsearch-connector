package org.rzlabs.elastic.metadata

import org.rzlabs.elastic.ElasticIndex

case class ElasticOptions(host: String,
                          index: String,
                          `type`: String,
                          poolMaxConnectionsPerRoute: Int,
                          poolMaxConnections: Int)

case class ElasticRelationInfo(name: String,
                               indexInfo: ElasticIndex,
                               options: ElasticOptions)