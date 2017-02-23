import Models.{ProductUnit, UID}
import akka.actor.{Actor, Props}

import scalaz.Scalaz._

/**
  * Created by yeryomenkom on 23.02.17.
  */
object Stock {

  def props() = Props(new Stock())

  case object GetAllUnits
  case class RequestUnits(productId: UID, count: Int)
  case class AddUnits(units: List[ProductUnit])

  case class AllUnits(quantitiesByProductId: Map[UID, List[ProductUnit]])
  case class RequestedUnits(productId: UID, units: List[ProductUnit])
  case class NotEnoughUnits(productId: UID, requestedQuantity: Int, availableQuantity: Int)
  case class AddedUnits(units: List[ProductUnit])

}

class Stock extends Actor {
  import Stock._

  var unitsByProductId: Map[UID, List[ProductUnit]] = Map.empty

  override def receive: Receive = {

    case RequestUnits(productId, count) =>
      val (requested, others) = unitsByProductId.getOrElse(productId, List.empty) splitAt count
      if (requested.size == count) {
        if (others.isEmpty) unitsByProductId = unitsByProductId - productId
        else unitsByProductId = unitsByProductId + (productId -> others)
        sender ! RequestedUnits(productId, requested)
      } else {
        sender ! NotEnoughUnits(productId, count, requested.size)
      }

    case AddUnits(units) =>
      unitsByProductId = unitsByProductId |+| units.groupBy(_.product.id)
      sender ! AddedUnits(units)

    case GetAllUnits =>
      sender ! AllUnits(unitsByProductId)
  }

}
