# shopping-basket-test
## Start application
Open console in root folder of project then type ```sbt run```
## API
### Models
Product. Example:

```json
{
  "description": "The most red tomato in the world.", 
  "id": 2, 
  "name": "tomato", 
  "price": 3.45
}
```
Product Unit. Example:
```json
{
  "id": 23, 
  "product": {
    "description": "The most red tomato in the world.", 
    "id": 2, 
    "name": "tomato", 
    "price": 3.45
  }
}
```
### Endpoints
 | GET | POST | DELETE
--- | :---: | :---: | :---:
api/catalog | Returns all available products. | - | -
api/shoppingbasket | Returns all users basket content grouped by userId. | - | -
api/shoppingbasket/{user_id} | Returns all product units from user basket. | Creates basket for user if needed and adds product units. Body example ```{ "productId": 2, "unitsCount": 3 }```. | Remove user basket.
api/shoppingbasket/{user_id}/{product_id} | Returns all units of product from user basket. | Add product units of product. Body example ```{ "unitsCount": 3 }```. | Remove from basket all units of product.
api/shoppingbasket/{user_id}/{product_id}/{unit_id} | Returns product of unit. | - | Remove current product unit from basket.

## Simplifications
1. Model indentifiers are simple numbers. In distributed systems we should use UUID or other collision free keys generation systems.
2. Not full test coverage.
3. Stock is a single actor, wich contains all units of all products as a collection.
