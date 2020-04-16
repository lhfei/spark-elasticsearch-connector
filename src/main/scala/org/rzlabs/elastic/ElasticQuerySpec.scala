package org.rzlabs.elastic

import java.io.InputStream

import com.fasterxml.jackson.annotation.{JsonProperty, JsonSubTypes, JsonTypeInfo}
import org.apache.spark.sql.sources.elastic.CloseableIterator
import org.apache.spark.sql.types.StructType
import org.rzlabs.elastic.client.{ElasticClient, ResultRow}
import org.rzlabs.elastic.metadata.{ElasticOptions, ElasticRelationColumn, ElasticRelationInfo}

sealed trait QuerySpec extends Product {

  val index: String
  val `type`: Option[String]

  def apply(is: InputStream,
            conn: ElasticClient,
            onDone: => Unit = (),
            fromList: Boolean = false): CloseableIterator[ResultRow]

  def schemaFromQuerySpec(info: ElasticRelationInfo): StructType

  def executeQuery(client: ElasticClient): CloseableIterator[ResultRow] = {
    client.executeQueryAsStream(this)
  }

  def mapSparkColNameToElasticColName(info: ElasticRelationInfo): Map[String, String] = Map()
}

sealed trait FilterSpec

/**
 * e.g.,
 * """
 *   {
 *     "range": {
 *       "publish_time": {
 *         "gte": "2020-01-01T00:00:00",
 *         "lt": "2021-01-01T00:00:00"
 *       }
 *     }
 *   }
 * """
 * @param range
 */
case class RangeFilterSpec(range: Map[String, Map[String, Any]]) extends FilterSpec

object RangeFilterSpec {

  def apply(ic: IntervalCondition): RangeFilterSpec = {
    val columnName = ic.dtGrp.elasticColumn.column
    val millis = ic.dt.getMillis
    var range = Map[String, Map[String, Any]]()
    ic.`type` match {
      case IntervalConditionType.EQ =>
        range += (columnName ->
          (IntervalConditionType.GTE.toString -> millis,
            IntervalConditionType.LTE.toString -> millis))
      case _ => range += (columnName -> (ic.`type`.toString -> millis))
    }
    new RangeFilterSpec(range)
  }

  def apply(ec: ElasticRelationColumn,
            conditionType: IntervalConditionType.Value,
            value: Any) = conditionType match {
    case IntervalConditionType.EQ =>
      new RangeFilterSpec(Map[String, Map[String, Any]](ec.column ->
        Map[String, Any](IntervalConditionType.GTE.toString -> value,
          IntervalConditionType.LTE.toString -> value)))
    case _ =>
      new RangeFilterSpec(Map[String, Map[String, Any]](ec.column ->
        Map[String, Any](conditionType.toString -> value)))
  }
}

case class TermFilterSpec(term: Map[String, Any]) extends FilterSpec

object TermFilterSpec {

  def apply(ec: ElasticRelationColumn, name: String, value: Any) = ec.dataType match {
    case ElasticDataType.Text if ec.keywordField.isDefined =>
      new TermFilterSpec(
        Map[String, Any](s"$name.${ec.keywordField.get}" -> value))
    case ElasticDataType.Text =>
      throw new ElasticIndexException(
        "Text type column without keyword field cannot be applied EqualTo expression.")
    case ElasticDataType.Unknown =>
      throw new ElasticIndexException(s"'$name' column is type-unknown.")
    case _ =>
      new TermFilterSpec(
        Map[String, Any](name -> value)
      )
  }
}

case class TermsFilterSpec(terms: Map[String, List[Any]]) extends FilterSpec {
  def this(name: String, vals: List[Any]) {
    this(Map[String, List[Any]](name -> vals))
  }
}

case class FieldSpec(field: String)

case class ExistsFilterSpec(exists: FieldSpec) extends FilterSpec {
  def this(name: String) {
    this(FieldSpec(name))
  }
}

case class InlineSpec(inline: String, lang: String) {
  def this(inline: String) {
    this(inline, "painless")
  }
}

case class InlineScriptSpec(script: InlineSpec)

case class ColumnComparisonFilterSpec(script: InlineScriptSpec) extends FilterSpec

object ColumnComparisonFilterSpec {

  def apply(ec1: ElasticRelationColumn, ec2: ElasticRelationColumn) = List(ec1, ec2) match {
    case list if list.exists(ec => ec.dataType == ElasticDataType.Text && ec.keywordField.isEmpty) =>
      throw new ElasticIndexException("Fielddata is disabled on text fields by default. ")
    case list if list.exists(_.dataType == ElasticDataType.Unknown) =>
      throw new ElasticIndexException("Has type-unknown column.")
    case list if list.exists(_.dataType == ElasticDataType.Text) =>
      val names = list.map { ec =>
        if (ec.dataType == ElasticDataType.Text && ec.keywordField.isDefined)
          s"${ec.column}.${ec.keywordField.get}"
        else ec.column
      }
      new ColumnComparisonFilterSpec(
        InlineScriptSpec(
          new InlineSpec(
            s"doc['${names(0)}'].value == doc['${names(1)}'].value"
          )
        )
      )
    case list =>
      new ColumnComparisonFilterSpec(
        InlineScriptSpec(
          new InlineSpec(
            s"doc['${ec1.column}'].value == doc['${ec2.column}'].value"
          )
        )
      )
  }
}

//sealed trait ConjExpressionFilterSpec extends FilterSpec
//
//case class ShouldExpressionFilterSpec(should: List[FilterSpec]) extends ConjExpressionFilterSpec
//
//case class MustExpressionFilterSpec(must: List[FilterSpec]) extends ConjExpressionFilterSpec
//
//case class MustNotExpressionFilterSpec(@JsonProperty("must_no")
//                                       mustNot: List[FilterSpec]) extends ConjExpressionFilterSpec
//
//case class FilterExpressionFilterSpec(filter: List[FilterSpec]) extends ConjExpressionFilterSpec

case class ConjExpressionFilterSpec(must: List[FilterSpec] = null,
                                   @JsonProperty("must_not") mustNot: List[FilterSpec] = null,
                                   should: List[FilterSpec] = null,
                                   filter: List[FilterSpec] = null)

case class BoolExpressionFilterSpec(bool: ConjExpressionFilerSpec) extends FilterSpec