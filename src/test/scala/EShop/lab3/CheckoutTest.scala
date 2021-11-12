package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import EShop.lab2.Checkout.{ConfirmPaymentReceived, PaymentStarted, SelectDeliveryMethod, SelectPayment, StartCheckout}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class CheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "send close confirmation to cart" in {
    val cartProbe     = testKit.createTestProbe[Checkout.Event]()
    val orderManagerProbe = testKit.createTestProbe[Any]()
    val checkoutActor = testKit.spawn(new Checkout(cartProbe.ref).start)

    checkoutActor ! StartCheckout
    checkoutActor ! SelectDeliveryMethod("delivery_method")
    checkoutActor ! SelectPayment("payment_method", orderManagerProbe.ref)
    checkoutActor ! ConfirmPaymentReceived

    orderManagerProbe.expectMessageType[Checkout.PaymentStarted]
    cartProbe.expectMessage(Checkout.CheckOutClosed)
  }

  it should "expire if no payment method selected" in {
    val cartProbe     = testKit.createTestProbe[Checkout.Event]()
    val checkoutActor = testKit.spawn(new Checkout(cartProbe.ref).start)

    checkoutActor ! StartCheckout
    checkoutActor ! SelectDeliveryMethod("delivery_method")

    cartProbe.expectNoMessage()
  }
}
