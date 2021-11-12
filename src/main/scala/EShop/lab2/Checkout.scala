package EShop.lab2

import EShop.lab2.Checkout.Event
import EShop.lab3.Payment
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {
  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(
    payment: String,
    orderManagerRef: ActorRef[Any]
  )                                  extends Command
  case object ExpirePayment          extends Command
  case object ConfirmPaymentReceived extends Command

  sealed trait Event
  case object CheckOutClosed                                    extends Event
  case class PaymentStarted(payment: ActorRef[Payment.Command]) extends Event
  case object CheckoutStarted                                   extends Event
  case object CheckoutCancelled                                 extends Event
  case class DeliveryMethodSelected(method: String)             extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(Some(timer))
  case class SelectingPaymentMethod(timer: Cancellable) extends State(Some(timer))
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(Some(timer))
}

class Checkout(
  cartActor: ActorRef[Event]
) {
  import Checkout._

  val checkoutTimerDuration: FiniteDuration = 5 seconds
  val paymentTimerDuration: FiniteDuration  = 5 seconds

  private def scheduleCheckoutTimer(context: ActorContext[Checkout.Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)

  private def schedulePaymentTimer(context: ActorContext[Checkout.Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[Checkout.Command] =
    Behaviors.receive { (context, msg) =>
      {
        msg match {
          case StartCheckout =>
            selectingDelivery(scheduleCheckoutTimer(context))
        }
      }
    }

  def selectingDelivery(timer: Cancellable): Behavior[Checkout.Command] =
    Behaviors.receive { (context, msg) =>
      {
        msg match {
          case SelectDeliveryMethod(_) =>
            timer.cancel()
            selectingPaymentMethod(scheduleCheckoutTimer(context))

          case ExpireCheckout | CancelCheckout =>
            timer.cancel()
            cancelled
        }
      }
    }

  def selectingPaymentMethod(timer: Cancellable): Behavior[Checkout.Command] =
    Behaviors.receive { (context, msg) =>
      {
        msg match {
          case SelectPayment(payment, orderManagerAdapter) =>
            timer.cancel()

            val paymentActor =
              context.spawn(new Payment(payment, orderManagerAdapter, context.self).start, "paymentActor")
            orderManagerAdapter ! PaymentStarted(paymentActor)

            processingPayment(schedulePaymentTimer(context))

          case ExpireCheckout | CancelCheckout =>
            timer.cancel()
            cancelled
        }
      }
    }

  def processingPayment(timer: Cancellable): Behavior[Checkout.Command] =
    Behaviors.receiveMessage {
      {
        case ExpirePayment | CancelCheckout =>
          timer.cancel()
          cancelled

        case ConfirmPaymentReceived =>
          timer.cancel()
          cartActor ! CheckOutClosed
          closed
      }
    }

  def cancelled: Behavior[Checkout.Command] = Behaviors.stopped

  def closed: Behavior[Checkout.Command] = Behaviors.stopped

}
