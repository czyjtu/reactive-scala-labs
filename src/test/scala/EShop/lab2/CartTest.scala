package EShop.lab2

import org.scalatest.flatspec.AnyFlatSpec
import EShop.lab2.Cart._

class CartTest extends AnyFlatSpec {

  it should "be non empty after adding item, and removing non existing one" in {
    assert(Cart.empty.addItem(1).removeItem(2).size == 1)
  }

  it should "be empty after removing last item" in {
    assert(Cart.empty.addItem(1).removeItem(1).size == 0)
  }
}
