import Models.{ProductUnit, UID}
import akka.actor.{Actor, ActorRef, Props}
import akka.actor.Actor.Receive

/**
  * Created by yeryomenkom on 25.02.17.
  */

object BasketsManager {

  def props() = Props(new BasketsManager())

  case object GetAllBaskets
  case class RequestBasket(userId: UID)
  case class RequestBasketCreateIfNeed(userId: UID)
  case class RemoveBasket(userId: UID)

  case class AllBaskets(basketsByUserId: Map[UID, ActorRef])
  case class RequestedBasket(userId: UID, basket: ActorRef)
  case class RemovedBasket(userId: UID, productUnits: Option[Seq[ProductUnit]])
  case class BasketNotFound(userId: UID)

}

class BasketsManager extends Actor {
  import BasketsManager._
  import context._

  private var userIds = Set.empty[UID]

  override def receive: Receive = {

    case GetAllBaskets =>
      val userIdsWithBaskets = for {
        userId <- userIds
        basket <- child(createBasketChildActorName(userId))
      } yield (userId, basket)
      sender ! AllBaskets(userIdsWithBaskets.toMap)

    case RequestBasket(userId) =>
      val name = createBasketChildActorName(userId)
      child(name) match {
        case Some(basket) =>
          sender ! RequestedBasket(userId, basket)
        case None =>
          sender ! BasketNotFound(userId)
      }

    case RequestBasketCreateIfNeed(userId) =>
      val name = createBasketChildActorName(userId)
      val basket = child(name) getOrElse {
        userIds = userIds + userId
        actorOf(Basket.props(userId), name)
      }
      sender ! RequestedBasket(userId, basket)

    case RemoveBasket(userId) =>
      val name = createBasketChildActorName(userId)
      child(name) match {
        case Some(basket) =>
          userIds = userIds - userId
          basket forward Basket.RemoveAllAndClose
        case None =>
          sender ! BasketNotFound(userId)
      }

  }

  def createBasketChildActorName(userId: UID) = s"basket_user_id_$userId"

}
