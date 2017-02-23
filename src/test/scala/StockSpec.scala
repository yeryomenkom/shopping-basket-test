import Models.ProductsGenerator
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{MustMatchers, WordSpecLike}

/**
  * Created by yeryomenkom on 23.02.17.
  */

class StockSpec extends TestKit(ActorSystem())
  with WordSpecLike with MustMatchers with ImplicitSender with StopSystemAfterAll
  with ProductsGenerator {
  import Stock._

  "The Stock" must {

    "add product units." in {

      val stock = system.actorOf(Stock.props())
      val productUnits = generateProductUnits()

      stock ! AddUnits(productUnits)
      expectMsg(AddedUnits(productUnits))

      stock ! GetAllUnits
      expectMsg(AllUnits(productUnits.groupBy(_.product.id)))
    }

    "give requested units if they are available." in {
      val stock = system.actorOf(Stock.props())
      val productUnits = generateProductUnits().groupBy(_.product.id).values.head.take(2)
      val productId = productUnits.head.product.id

      stock ! AddUnits(productUnits)
      expectMsg(AddedUnits(productUnits))

      stock ! RequestUnits(productId, productUnits.size)
      expectMsg(RequestedUnits(productId, productUnits))

      stock ! GetAllUnits
      expectMsg(AllUnits(Map.empty))
    }

    "do not give requested units if not all of them are available." in {
      val stock = system.actorOf(Stock.props())

      val productId = 3
      val unitsCount = 4

      stock ! RequestUnits(productId, unitsCount)
      expectMsg(NotEnoughUnits(productId, unitsCount, 0))
    }

  }

}
