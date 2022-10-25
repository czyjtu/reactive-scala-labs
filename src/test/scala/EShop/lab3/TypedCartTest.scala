package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import EShop.lab2.TypedCheckout
import akka.actor.typed.ActorRef

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  it should "add item properly" in {
    val testKit = BehaviorTestKit(TypedCartActor())
    val inbox = TestInbox[Cart]()
    val itemId = "someItem"

    testKit.run(AddItem(itemId))
    testKit.run(GetItems(inbox.ref))
    inbox.expectMessage(Cart(List(itemId)))
  }

  it should "be empty after adding and removing the same item" in {
    val testKit = BehaviorTestKit(TypedCartActor())
    val inbox = TestInbox[Cart]()
    val itemId = "someItem"

    testKit.run(AddItem(itemId))
    testKit.run(RemoveItem(itemId))
    testKit.run(GetItems(inbox.ref))
    inbox.expectMessage(Cart.empty)
  }

  it should "start checkout" in {
    val cartActor = testKit.spawn(TypedCartActor())
    val probe = testKit.createTestProbe[OrderManager.Command]()
    
    cartActor ! AddItem("someItem")
    cartActor ! StartCheckout(probe.ref)
    val msg = probe.receiveMessage()
    assert(msg.isInstanceOf[OrderManager.ConfirmCheckoutStarted])
  }
}
