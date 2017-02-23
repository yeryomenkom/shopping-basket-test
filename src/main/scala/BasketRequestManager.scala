import Models.{ProductUnit, UID}
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.util.Timeout

/**
  * Created by yeryomenkom on 23.02.17.
  */

object BasketRequestManager {

  def props(stock: ActorRef, basket: ActorRef)(implicit timeout: Timeout) =
    Props(new BasketRequestManager(stock, basket, timeout))

  case object GetAllUnits
  case class GetProductUnits(productId: UID)
  case class GetUnit(unitId: UID)
  case class AddToBasket(productId: UID, count: Int)
  case object RemoveAllUnits
  case class RemoveProductUnits(productId: UID)
  case class RemoveUnit(unitId: UID)

  case class ProductUnits(units: Seq[ProductUnit])
  case class AddedUnits(units: Seq[ProductUnit])
  case class NotEnoughStockUnits(requestedUnitsCount: Int, availableUnitsCount: Int)
  case class RemovedUnits(units: List[ProductUnit])

}

class BasketRequestManager(stock: ActorRef, basket: ActorRef, timeout: Timeout) extends Actor with ActorLogging {
  import BasketRequestManager._
  import context._

  system.scheduler.scheduleOnce(timeout.duration, self, PoisonPill)

  override def receive: Receive = waitingForRequest

  private def waitingForRequest: Receive = {

    case GetAllUnits =>
      basket ! Basket.Get()
      become(gettingBasketUnitsList(sender))

    case GetProductUnits(productId) =>
      basket ! Basket.Get(_.product.id == productId)
      become(gettingBasketUnitsList(sender))

    case GetUnit(unitId) =>
      basket ! Basket.Get(_.id == unitId)
      become(gettingBasketUnitsList(sender))

    case AddToBasket(productId, count) =>
      stock ! Stock.RequestUnits(productId, count)
      become(addingUnitsToBasket(sender))

    case RemoveAllUnits =>
      basket ! Basket.Remove()
      become(removingUnitsFromBasket(sender))

    case RemoveProductUnits(productId) =>
      basket ! Basket.Remove(_.product.id == productId)
      become(removingUnitsFromBasket(sender))

    case RemoveUnit(unitId) =>
      basket ! Basket.Remove(_.id == unitId)
      become(removingUnitsFromBasket(sender))

  }

  private def gettingBasketUnitsList(requestSender: ActorRef): Receive = {

    case Basket.Units(units) =>
      requestSender ! ProductUnits(units)
      stop(self)

  }

  private def addingUnitsToBasket(requestSender: ActorRef): Receive = {

    case Stock.RequestedUnits(_, units) =>
      basket ! Basket.Add(units)

    case Stock.NotEnoughUnits(_, requestedCount, availableCount) =>
      requestSender ! NotEnoughStockUnits(requestedCount, availableCount)
      stop(self)

    case Basket.Added(units) =>
      requestSender ! AddedUnits(units)
      stop(self)

  }

  private def removingUnitsFromBasket(requestSender: ActorRef): Receive = {

    case Basket.Removed(units) =>
      stock ! Stock.AddUnits(units)

    case Stock.AddedUnits(units) =>
      requestSender ! RemovedUnits(units)
      stop(self)

  }

}
