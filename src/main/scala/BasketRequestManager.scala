import Models.{ProductUnit, UID}
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.util.Timeout

/**
  * Created by yeryomenkom on 23.02.17.
  */

object BasketRequestManager {

  def props(stock: ActorRef, basketsManager: ActorRef)(implicit timeout: Timeout) =
    Props(new BasketRequestManager(stock, basketsManager, timeout))

  case object GetAllBasketsContent
  case class GetAllUnits(userId: UID)
  case class GetProductUnits(userId: UID, productId: UID)
  case class GetUnit(userId: UID, unitId: UID)
  case class AddToBasket(userId: UID, productId: UID, count: Int)
  case class RemoveAllUnits(userId: UID)
  case class RemoveProductUnits(userId: UID, productId: UID)
  case class RemoveUnit(userId: UID, unitId: UID)
  case class RemoveBasket(userId: UID)

  case class AllBasketsContent(unitsByUserId: Map[UID, List[ProductUnit]])
  case class ProductUnits(units: List[ProductUnit])
  case class AddedUnits(units: List[ProductUnit])
  case class NotEnoughStockUnits(requestedUnitsCount: Int, availableUnitsCount: Int)
  case class RemovedUnits(units: List[ProductUnit])
  case class BasketRemoved(userId: UID, units: List[ProductUnit])
  case class BasketNotFound(userId: UID)

}

class BasketRequestManager(stock: ActorRef, basketsManager: ActorRef, timeout: Timeout) extends Actor {
  import BasketRequestManager._
  import context._

  system.scheduler.scheduleOnce(timeout.duration, self, PoisonPill)

  override def receive: Receive = waitingForRequest

  private def waitingForRequest: Receive = {

    case GetAllBasketsContent =>
      basketsManager ! BasketsManager.GetAllBaskets
      become(gettingAllBasketsContent(sender))

    case AddToBasket(userId, productId, count) =>
      stock ! Stock.RequestUnits(productId, count)
      become(requestingUnitsFromStock(sender, userId))

    case GetAllUnits(userId) =>
      basketsManager ! BasketsManager.RequestBasket(userId)
      become(gettingBasketUnitsList(sender, _ => true))

    case GetProductUnits(userId, productId) =>
      basketsManager ! BasketsManager.RequestBasket(userId)
      become(gettingBasketUnitsList(sender, _.product.id == productId))

    case GetUnit(userId, unitId) =>
      basketsManager ! BasketsManager.RequestBasket(userId)
      become(gettingBasketUnitsList(sender, _.id == unitId))

    case RemoveAllUnits(userId) =>
      basketsManager ! BasketsManager.RequestBasket(userId)
      become(removingUnitsFromBasket(sender, _ => true))

    case RemoveProductUnits(userId, productId) =>
      basketsManager ! BasketsManager.RequestBasket(userId)
      become(removingUnitsFromBasket(sender, _.product.id == productId))

    case RemoveUnit(userId, unitId) =>
      basketsManager ! BasketsManager.RequestBasket(userId)
      become(removingUnitsFromBasket(sender, _.id == unitId))

    case RemoveBasket(userId) =>
      basketsManager ! BasketsManager.RemoveBasket(userId)
      become(removingBasket(sender, userId))

  }

  private def gettingAllBasketsContent(requestSender: ActorRef, totalUsersCount: Int = -1,
                                       unitsAcc: List[(UID, List[ProductUnit])] = List.empty): Receive = {

    case BasketsManager.AllBaskets(basketsByUserId) =>
      if (basketsByUserId.isEmpty) {
        requestSender ! AllBasketsContent(Map.empty)
        stop(self)
      } else {
        basketsByUserId foreach { case (_, basket) => basket ! Basket.Get(_ => true) }
        become(gettingAllBasketsContent(requestSender, basketsByUserId.keySet.size))
      }

    case Basket.Units(userId, units) =>
      val newUnitsAcc = unitsAcc :+ userId -> units
      if (newUnitsAcc.size == totalUsersCount) {
        requestSender ! AllBasketsContent(newUnitsAcc.toMap)
        stop(self)
      } else {
        become(gettingAllBasketsContent(requestSender, totalUsersCount, newUnitsAcc))
      }

  }

  private def requestingUnitsFromStock(requestSender: ActorRef, userId: Int): Receive = {

    case Stock.RequestedUnits(_, units) =>
      basketsManager ! BasketsManager.RequestBasketCreateIfNeed(userId)
      become(addingUnitsToBasket(requestSender, units))

    case Stock.NotEnoughUnits(_, requestedCount, availableCount) =>
      requestSender ! NotEnoughStockUnits(requestedCount, availableCount)
      stop(self)

  }

  private def addingUnitsToBasket(requestSender: ActorRef, units: List[ProductUnit]): Receive = {

    case BasketsManager.RequestedBasket(_, basket) =>
      basket ! Basket.Add(units)

    case Basket.Added(_, _) =>
      requestSender ! AddedUnits(units)
      stop(self)

  }

  private def gettingBasketUnitsList(requestSender: ActorRef, predicate: ProductUnit => Boolean): Receive = {

    case BasketsManager.RequestedBasket(_, basket) =>
      basket ! Basket.Get(predicate)

    case BasketsManager.BasketNotFound(userId) =>
      requestSender ! BasketNotFound(userId)
      stop(self)

    case Basket.Units(_, units) =>
      requestSender ! ProductUnits(units)
      stop(self)

  }

  private def removingUnitsFromBasket(requestSender: ActorRef, predicate: ProductUnit => Boolean): Receive = {

    case BasketsManager.RequestedBasket(_, basket) =>
      basket ! Basket.Remove(predicate)

    case BasketsManager.BasketNotFound(userId) =>
      requestSender ! BasketNotFound(userId)
      stop(self)

    case Basket.Removed(_, units) =>
      stock ! Stock.AddUnits(units)

    case Stock.AddedUnits(units) =>
      requestSender ! RemovedUnits(units)
      stop(self)

  }

  private def removingBasket(requestSender: ActorRef, userId: UID): Receive = {

    case BasketsManager.BasketNotFound(_) =>
      requestSender ! BasketNotFound(userId)
      stop(self)

    case Basket.Removed(_, units) =>
      stock ! Stock.AddUnits(units)

    case Stock.AddedUnits(units) =>
      requestSender ! BasketRemoved(userId, units)
      stop(self)

  }

}
