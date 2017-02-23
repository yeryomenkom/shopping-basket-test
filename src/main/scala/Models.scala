/**
  * Created by yeryomenkom on 23.02.17.
  */

object Models {

  type UID = Int
  type Price = BigDecimal

  case class Product(id: UID, name: String, price: Price, description: String)
  case class ProductUnit(id: UID, product: Product)

  object Rest {
    case class UnitsCount(unitsCount: Int)
    case class ProductUnitsCount(productId: Int, unitsCount: Int)
  }

  case class ProductIdWithUnitsCount(productId: UID, unitCount: Int)
  case class ProductBundle(product: Product, quantity: Int)

  trait ProductsGenerator {

    def generateProductUnits() = {

      var intStream = Stream.from(5, 2)
      def getNextInt = {
        val i = intStream.head
        intStream = intStream.tail
        i
      }

      val products = List(
        Product(1, "banana", 5.45, "The most yellow bananas in the world."),
        Product(2, "tomato", 3.45, "The most red tomato in the world."),
        Product(3, "orange", 2.56, "Usual orange.")
      )

      def generateProductUnits(n: Int, p: Product) = (1 to n).map(_ => ProductUnit(getNextInt, p))

      products.flatMap(p => generateProductUnits(getNextInt, p))
    }

  }

}
