package org.rzlabs.elastic

import java.util.concurrent.atomic.AtomicLong

import org.apache.spark.sql.catalyst.expressions.{Expression, NamedExpression}
import org.apache.spark.sql.catalyst.plans.logical.Aggregate
import org.apache.spark.sql.types.DataType
import org.rzlabs.elastic.metadata.{ElasticRelationColumn, ElasticRelationInfo}

import scala.collection.mutable.{Map => MMap}

case class ElasticQueryBuilder(relationInfo: ElasticRelationInfo,
//                               queryIntervals: QueryIntervals,
                               referenceElasticColumns: MMap[String, ElasticRelationColumn] = MMap(),
                               limitSpec: Option[LimitSpec] = None,
                               sortSpec: Option[SortSpec] = None,
                               offsetSpec: Option[OffsetSpec] = None,
                               filterSpec: Option[FilterSpec] = None,
                               projectionAliasMap: Map[String, String] = Map(),
                               outputAttributeMap: Map[String, (Expression, DataType, DataType, String)] = Map(),
                               avgExpressions: Map[Expression, (String, String)] = Map(),
                               aggregateOp: Option[Aggregate] = None,
                               curId: AtomicLong = new AtomicLong(-1),
                               origProjectList: Option[Seq[NamedExpression]] = None,
                               origFilter: Option[Expression] = None,
                               hasUnpushedProjections: Boolean = false,
                               hasUnpushedFilters: Boolean = false,
                               hasOffsetAggregate: Boolean = false) {

  def elasticCoumn(name: String): Option[ElasticRelationColumn] = {
    relationInfo.indexInfo.columns.get(projectionAliasMap.getOrElse(name, name)).map {
      elasticColumn =>
        val elasticRelationColumn = ElasticRelationColumn(name, Some(elasticColumn))
        referenceElasticColumns(name) = elasticRelationColumn
        elasticRelationColumn
    }
  }

  def addAlias(alias: String, col: String) = {
    val colName = projectionAliasMap.getOrElse(col, col)
    this.copy(projectionAliasMap = projectionAliasMap + (alias -> colName))
  }

  /**
   * From "alias-1" to "alias-N"
   * @return
   */
  def nextAlias: String = s"alias${curId.getAndDecrement()}"

//  def queryInterval(ic: IntervalCondition): Option[ElasticQueryBuilder] = ic.`type` match {
//    case IntervalConditionType.LT =>
//      queryIntervals.ltCond(ic.dt).map(qi => this.copy(queryIntervals = qi))
//    case IntervalConditionType.LTE =>
//      queryIntervals.lteCond(ic.dt).map(qi => this.copy(queryIntervals = qi))
//    case IntervalConditionType.GT =>
//      queryIntervals.gtCond(ic.dt).map(qi => this.copy(queryIntervals = qi))
//    case IntervalConditionType.GTE =>
//      queryIntervals.gteCond(ic.dt).map(qi => this.copy(queryIntervals = qi))
//
//  }

  def filterSpecification(f: FilterSpec) = (filterSpec, f) match {
    case (Some(fs), _) =>
      //this.copy(filterSpec =
      //  Some(BoolExpressionFilterSpec(ConjExpressionFilterSpec(must = List(f, fs)))))
      this.copy(filterSpec = Some(BoolExpressionFilterSpec(
        ConjExpressionFilterSpec(mustOpt = Some(List(f, fs)), mustNotOpt = None, shouldOpt = None, filterOpt = None))))
    case (None, _) =>
      this.copy(filterSpec = Some(f))
  }

  def outputAttribute(name: String, e: Expression, originalDT: DataType,
                      elasticDT: DataType, tfName: String = null) = {
    val tf = if (tfName == null) ElasticValTransform.getTFName(elasticDT) else tfName
    this.copy(outputAttributeMap = outputAttributeMap + (name -> (e, originalDT, elasticDT, tf)))
  }

  def limit(l: LimitSpec) = {
    this.copy(limitSpec = Some(l))
  }

  //def limit(amt: Int): Option[ElasticQueryBuilder] = Some(limit(LimitSpec(amt)))

  def limit(amt: Int): Option[ElasticQueryBuilder] = limitSpec match {
    case Some(spec) => Some(this)
    case None => Some(limit(LimitSpec(amt)))
  }

  def offset(o: OffsetSpec) = {
    this.copy(offsetSpec = Some(o))
  }

  def offset(amt: Int): Option[ElasticQueryBuilder] = Some(offset(OffsetSpec(amt)))

  def orderBy(s: SortSpec) = {
    this.copy(sortSpec = Some(s))
  }

  def orderBy(name: String, order: Order.Value): Option[ElasticQueryBuilder] = elasticCoumn(name) match {
    case Some(ec) => sortSpec match {
      case None => Some(orderBy(SortSpec(ec, name, order)))
      case _ => Some(orderBy(sortSpec.get + SortSpec(ec, name, order)))
    }
    case None => throw new ElasticIndexException("Unknown field.")

  }

  def offsetAggregate(b: Boolean) = Some(this.copy(hasOffsetAggregate = b))
}

object ElasticQueryBuilder {
  def apply(relationInfo: ElasticRelationInfo): ElasticQueryBuilder =
    new ElasticQueryBuilder(relationInfo)
}
