import Models.ProductsGenerator
import akka.actor.ActorSystem
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

    "properly get all product units from basket." in {
      val stock = TestProbe()
      val basket = TestProbe()

      val basketRequestManager = system.actorOf(BasketRequestManager.props(stock.testActor, basket.testActor))

      basketRequestManager ! BasketRequestManager.GetAllUnits(userId)
      basket.expectMsg(Basket.Get(_ => true))
      basket.send(basketRequestManager, Basket.Units(userId, productUnits))

      expectMsg(BasketRequestManager.ProductUnits(productUnits))
    }

    "properly add product units to basket." in {
      val stock = TestProbe()
      val basket = TestProbe()

      val basketRequestManager = system.actorOf(BasketRequestManager.props(stock.testActor, basket.testActor))

      basketRequestManager ! BasketRequestManager.AddToBasket(userId, product.id, productUnits.size)
      stock.expectMsg(Stock.RequestUnits(product.id, productUnits.size))
      stock.send(basketRequestManager, Stock.RequestedUnits(product.id, productUnits))

      basket.expectMsg(Basket.Add(productUnits))
      basket.send(basketRequestManager, Basket.Added(userId, productUnits))

      expectMsg(BasketRequestManager.AddedUnits(productUnits))
    }

    "properly remove all product units from basket." in {
      val stock = TestProbe()
      val basket = TestProbe()

      val basketRequestManager = system.actorOf(BasketRequestManager.props(stock.testActor, basket.testActor))

      basketRequestManager ! BasketRequestManager.RemoveAllUnits(userId)
      basket.expectMsg(Basket.Remove(_ => true))
      basket.send(basketRequestManager, Basket.Removed(userId, productUnits))

      stock.expectMsg(Stock.AddUnits(productUnits))
      stock.send(basketRequestManager, Stock.AddedUnits(productUnits))

      expectMsg(BasketRequestManager.RemovedUnits(productUnits))
    }

  }

}
