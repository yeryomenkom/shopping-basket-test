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

  val basket = system.actorOf(Basket.props(), "basket")
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
          post {
            entity(as[Models.Rest.ProductUnitsCount]) { puc =>
              onSuccess(createRequestManagerActor() ? AddToBasket(puc.productId, puc.unitsCount)) {
                case AddedUnits(units) =>
                  complete(units)
                case NotEnoughStockUnits(requested, available) =>
                  complete(StatusCodes.InternalServerError,
                    s"Not enough product items on stock. Requested: $requested. Available: $available.")
              }
            }
          } ~
          get {
            onSuccess(createRequestManagerActor() ? GetAllUnits) {
              case ProductUnits(units) => complete(units)
            }
          } ~
          delete {
            onSuccess(createRequestManagerActor() ? RemoveAllUnits) {
              case RemovedUnits(units) => complete(s"${units.size} units removed.")
            }
          }
        } ~
        path(IntNumber / IntNumber) { case (productId, unitId) =>
          get {
            onSuccess(createRequestManagerActor() ? GetUnit(unitId)) {
              case ProductUnits(units) =>
                units.headOption.fold(complete(StatusCodes.NotFound))(complete(_))
            }
          } ~
          delete {
            onSuccess(createRequestManagerActor() ? RemoveUnit(unitId)) {
              case RemovedUnits(units) => complete(s"${units.size} units removed.")
            }
          }
        } ~
        path(IntNumber) { productId =>
          post {
            entity(as[Models.Rest.UnitsCount]) { uc =>
              onSuccess(createRequestManagerActor() ? AddToBasket(productId, uc.unitsCount)) {
                case AddedUnits(units) =>
                  complete(units)
                case NotEnoughStockUnits(requested, available) =>
                  complete(StatusCodes.InternalServerError,
                    s"Not enough product items on stock. Requested: $requested. Available: $available.")
              }
            }
          } ~
          get {
            onSuccess(createRequestManagerActor() ? GetProductUnits(productId)) {
              case ProductUnits(units) => complete(units)
            }
          } ~
          delete {
            onSuccess(createRequestManagerActor() ? RemoveProductUnits(productId)) {
              case RemovedUnits(units) => complete(s"${units.size} units removed.")
            }
          }
        }
      }
    }
  }

  Http().bindAndHandle(routes, "localhost", 9001)

}
