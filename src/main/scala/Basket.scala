import Models.{ProductUnit, UID}
import akka.actor.{Actor, Props}

/**
  * Created by yeryomenkom on 23.02.17.
  */

object Basket {

  def props(userId: UID) = Props(new Basket(userId))

  case class Get(predicate: ProductUnit => Boolean)
  case class Add(units: List[ProductUnit])
  case class Remove(predicate: ProductUnit => Boolean)
  case object RemoveAllAndClose

  case class Units(userId: UID, units: List[ProductUnit])
  case class Added(userId: UID, units: List[ProductUnit])
  case class Removed(userId: UID, units: List[ProductUnit])

}

class Basket(val userId: UID) extends Actor {
  import Basket._
  import context._

  private var currentProductUnits: List[ProductUnit] = List.empty

  override def receive: Receive = {

    case Get(predicate) =>
      sender ! Units(userId, currentProductUnits filter predicate)

    case Add(units) =>
      currentProductUnits = currentProductUnits ::: units
      sender ! Added(userId, units)

    case Remove(predicate) =>
      val (removed, others) = currentProductUnits partition predicate
      currentProductUnits = others
      sender ! Removed(userId, removed)

    case RemoveAllAndClose =>
      sender ! Removed(userId, currentProductUnits)
      stop(self)

  }

}
