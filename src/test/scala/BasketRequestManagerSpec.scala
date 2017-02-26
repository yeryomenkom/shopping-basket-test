import Models.{ProductUnit, ProductsGenerator, UID}
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by yeryomenkom on 23.02.17.
  */

class BasketRequestManagerSpec extends TestKit(ActorSystem())
  with WordSpecLike with MustMatchers with ImplicitSender with StopSystemAfterAll
  with ProductsGenerator  {

  val productUnits = generateProductUnits().groupBy(_.product.id).values.head.take(2)
  val product = productUnits.head.product
  val userId = 2

  implicit val timeout: Timeout = Timeout(10 seconds)

  "The Basket Request Manager" must {

    "properly get all product units from all baskets." in {
      val stock = TestProbe()
      val basketsManager = TestProbe()

      val firstUserId = 1
      val secondUserId = 2

      val firstUserBasket = TestProbe()
      val secondUserBasket = TestProbe()

      val basketsByUserId: Map[UID, ActorRef] =
        Map(firstUserId -> firstUserBasket.testActor, secondUserId -> secondUserBasket.testActor)

      val (firstUserProducts, secondUserProducts) = productUnits splitAt productUnits.size/2
      val productUnitsByUserId: Map[UID, List[ProductUnit]] =
        Map(firstUserId -> firstUserProducts, secondUserId -> secondUserProducts)

      val basketRequestManager = system.actorOf(BasketRequestManager.props(stock.testActor, basketsManager.testActor))

      basketRequestManager ! BasketRequestManager.GetAllBasketsContent
      basketsManager.expectMsg(BasketsManager.GetAllBaskets)

      basketsManager.send(basketRequestManager, BasketsManager.AllBaskets(basketsByUserId))
      firstUserBasket.expectMsgType[Basket.Get]
      secondUserBasket.expectMsgType[Basket.Get]

      firstUserBasket.send(basketRequestManager, Basket.Units(firstUserId, productUnitsByUserId(firstUserId)))
      secondUserBasket.send(basketRequestManager, Basket.Units(secondUserId, productUnitsByUserId(secondUserId)))
      expectMsg(BasketRequestManager.AllBasketsContent(productUnitsByUserId))
    }

    "properly get all product units from basket." in {
      val stock = TestProbe()
      val basketsManager = TestProbe()
      val basket = TestProbe()

      val basketRequestManager = system.actorOf(BasketRequestManager.props(stock.testActor, basketsManager.testActor))

      basketRequestManager ! BasketRequestManager.GetAllUnits(userId)
      basketsManager.expectMsg(BasketsManager.RequestBasket(userId))

      basketsManager.send(basketRequestManager, BasketsManager.RequestedBasket(userId, basket.testActor))
      basket.expectMsgType[Basket.Get]

      basket.send(basketRequestManager, Basket.Units(userId, productUnits))
      expectMsg(BasketRequestManager.ProductUnits(productUnits))
    }

    "properly add product units to basket." in {
      val stock = TestProbe()
      val basket = TestProbe()
      val basketsManager = TestProbe()

      val basketRequestManager = system.actorOf(BasketRequestManager.props(stock.testActor, basketsManager.testActor))

      basketRequestManager ! BasketRequestManager.AddToBasket(userId, product.id, productUnits.size)
      stock.expectMsg(Stock.RequestUnits(product.id, productUnits.size))

      stock.send(basketRequestManager, Stock.RequestedUnits(product.id, productUnits))
      basketsManager.expectMsg(BasketsManager.RequestBasketCreateIfNeed(userId))

      basketsManager.send(basketRequestManager, BasketsManager.RequestedBasket(userId, basket.testActor))
      basket.expectMsg(Basket.Add(productUnits))

      basket.send(basketRequestManager, Basket.Added(userId, productUnits))
      expectMsg(BasketRequestManager.AddedUnits(productUnits))
    }

    "properly remove all product units from basket." in {
      val stock = TestProbe()
      val basket = TestProbe()
      val basketsManager = TestProbe()

      val basketRequestManager = system.actorOf(BasketRequestManager.props(stock.testActor, basketsManager.testActor))

      basketRequestManager ! BasketRequestManager.RemoveAllUnits(userId)
      basketsManager.expectMsg(BasketsManager.RequestBasket(userId))

      basketsManager.send(basketRequestManager, BasketsManager.RequestedBasket(userId, basket.testActor))
      basket.expectMsgType[Basket.Remove]

      basket.send(basketRequestManager, Basket.Removed(userId, productUnits))
      stock.expectMsg(Stock.AddUnits(productUnits))

      stock.send(basketRequestManager, Stock.AddedUnits(productUnits))
      expectMsg(BasketRequestManager.RemovedUnits(productUnits))
    }

    "properly remove basket." in {
      val stock = TestProbe()
      val basket = TestProbe()
      val basketsManager = TestProbe()

      val basketRequestManager = system.actorOf(BasketRequestManager.props(stock.testActor, basketsManager.testActor))

      basketRequestManager ! BasketRequestManager.RemoveBasket(userId)
      basketsManager.expectMsg(BasketsManager.RemoveBasket(userId))

      basket.send(basketRequestManager, Basket.Removed(userId, productUnits))
      stock.expectMsg(Stock.AddUnits(productUnits))

      stock.send(basketRequestManager, Stock.AddedUnits(productUnits))
      expectMsg(BasketRequestManager.BasketRemoved(userId, productUnits))
    }

  }

}
