import Models.ProductUnit
import akka.actor.{Actor, Props}

/**
  * Created by yeryomenkom on 23.02.17.
  */

object Basket {

  def props() = Props(new Basket())

  case class Get(predicate: ProductUnit => Boolean = _ => true)
  case class Add(units: List[ProductUnit])
  case class Remove(predicate: ProductUnit => Boolean = _ => true)

  case class Units(units: List[ProductUnit])
  case class Added(units: List[ProductUnit])
  case class Removed(units: List[ProductUnit])

}

class Basket extends Actor {
  import Basket._

  private var currentProductUnits: List[ProductUnit] = List.empty

  override def receive: Receive = {

    case Get(predicate) =>
      sender ! Units(currentProductUnits filter predicate)

    case Add(units) =>
      currentProductUnits = currentProductUnits ::: units
      sender ! Added(units)

    case Remove(predicate) =>
      val (removed, others) = currentProductUnits partition predicate
      currentProductUnits = others
      sender ! Removed(removed)

  }

}
