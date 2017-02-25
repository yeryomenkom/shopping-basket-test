import Models.ProductsGenerator
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.CirceSupport

import scala.concurrent.duration._

/**
  * Created by yeryomenkom on 23.02.17.
  */

object Application extends App with ProductsGenerator {

  implicit val system = ActorSystem("shopping-basket-system")
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  implicit val timeout: Timeout = Timeout(10 seconds)

  val productUnits = generateProductUnits()
  val products = productUnits.map(_.product).distinct

  val basket = system.actorOf(BasketsManager.props(), "baskets_manager")
  val stock = system.actorOf(Stock.props(), "stock")

  stock ! Stock.AddUnits(productUnits)

  def createRequestManagerActor() = system.actorOf(BasketRequestManager.props(stock, basket))

  val routes: Route = {
    import BasketRequestManager._
    import CirceSupport._
    import io.circe.generic.auto._

    pathPrefix("api") {
      path("catalog") {
        get {
          complete(products)
        }
      } ~
      pathPrefix("shoppingbasket") {
        pathEnd {
          get {
            onSuccess(createRequestManagerActor() ? GetAllBasketsContent) {
              case AllBasketsContent(unitsByUserId) =>
                complete(unitsByUserId)
            }
          }
        } ~
        path(IntNumber / IntNumber / IntNumber) { case (userId, productId, unitId) =>
          get {
            onSuccess(createRequestManagerActor() ? GetUnit(userId, unitId)) {
              case ProductUnits(units) =>
                units.headOption.fold(complete(StatusCodes.NotFound))(complete(_))
              case BasketNotFound(_) => complete(StatusCodes.NotFound,
                s"Basket for user $userId does not exist.")
            }
          } ~
          delete {
            onSuccess(createRequestManagerActor() ? RemoveUnit(userId, unitId)) {
              case RemovedUnits(units) => complete(s"${units.size} units removed.")
              case BasketNotFound(_) => complete(StatusCodes.NotFound,
                s"Basket for user $userId does not exist.")
            }
          }
        } ~
        path(IntNumber / IntNumber) { case (userId, productId) =>
          post {
            entity(as[Models.Rest.UnitsCount]) { uc =>
              onSuccess(createRequestManagerActor() ? AddToBasket(userId, productId, uc.unitsCount)) {
                case AddedUnits(units) =>
                  complete(units)
                case NotEnoughStockUnits(requested, available) =>
                  complete(StatusCodes.InternalServerError,
                    s"Not enough product items on stock. Requested: $requested. Available: $available.")
              }
            }
          } ~
          get {
            onSuccess(createRequestManagerActor() ? GetProductUnits(userId, productId)) {
              case ProductUnits(units) => complete(units)
              case BasketNotFound(_) => complete(StatusCodes.NotFound,
                s"Basket for user $userId does not exist.")
            }
          } ~
          delete {
            onSuccess(createRequestManagerActor() ? RemoveProductUnits(userId, productId)) {
              case RemovedUnits(units) => complete(s"${units.size} units removed.")
              case BasketNotFound(_) => complete(StatusCodes.NotFound,
                s"Basket for user $userId does not exist.")
            }
          }
        } ~
        path(IntNumber) { userId =>
          post {
            entity(as[Models.Rest.ProductUnitsCount]) { puc =>
              onSuccess(createRequestManagerActor() ? AddToBasket(userId, puc.productId, puc.unitsCount)) {
                case AddedUnits(units) =>
                  complete(units)
                case NotEnoughStockUnits(requested, available) =>
                  complete(StatusCodes.InternalServerError,
                    s"Not enough product items on stock. Requested: $requested. Available: $available.")
              }
            }
          } ~
          get {
            onSuccess(createRequestManagerActor() ? GetAllUnits(userId)) {
              case ProductUnits(units) => complete(units)
              case BasketNotFound(_) => complete(StatusCodes.NotFound,
                s"Basket for user $userId does not exist.")
            }
          } ~
          delete {
            onSuccess(createRequestManagerActor() ? RemoveBasket(userId)) {
              case BasketRemoved(_, units) =>
                complete(s"Basket for user $userId removed. ${units.size} units removed.")
              case BasketNotFound(_) => complete(StatusCodes.NotFound,
                s"Basket for user $userId does not exist.")
            }
          }
        }
      }
    }
  }

  Http().bindAndHandle(routes, "localhost", 9001)

}
