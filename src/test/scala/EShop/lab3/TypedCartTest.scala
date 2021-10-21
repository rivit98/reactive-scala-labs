package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier

  //async
  it should "add item properly" in {
    val cartActor = testKit.spawn(new TypedCartActor().start)
    val probe     = testKit.createTestProbe[Cart]()
    cartActor ! AddItem("item1")
    cartActor ! GetItems(probe.ref)
    probe.expectMessage(Cart(Seq("item1")))
  }

  // synchronous test
  it should "be empty after adding and removing the same item" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox   = TestInbox[Cart]()
    testKit.run(AddItem("item1"))
    testKit.run(RemoveItem("item1"))
    testKit.run(GetItems(inbox.ref))
    inbox.expectMessage(Cart.empty)
  }

  //async
  it should "start checkout" in {
    val cartActor = testKit.spawn(new TypedCartActor().start)
    val probe     = testKit.createTestProbe[Any]()
    cartActor ! AddItem("item1")
    cartActor ! StartCheckout(probe.ref)
    probe.expectMessageType[TypedCartActor.Event]
  }
}

