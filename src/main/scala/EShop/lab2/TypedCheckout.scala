package EShop.lab2

import EShop.lab2.TypedCheckout.Event
import EShop.lab3.Payment
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCheckout {
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

class TypedCheckout(
  cartActor: ActorRef[Event]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 5 seconds
  val paymentTimerDuration: FiniteDuration  = 5 seconds

  private def scheduleCheckoutTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)

  private def schedulePaymentTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[TypedCheckout.Command] =
    Behaviors.receive { (context, msg) =>
      {
        msg match {
          case StartCheckout =>
            selectingDelivery(scheduleCheckoutTimer(context))
        }
      }
    }

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
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

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] =
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

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] =
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

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] = Behaviors.stopped

}
