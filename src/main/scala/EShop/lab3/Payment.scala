package EShop.lab3

import EShop.lab2.Checkout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  case object DoPayment extends Command

  sealed trait Event
  case object PaymentReceived extends Event
}

class Payment(
  method: String,
  orderManager: ActorRef[Any],
  checkout: ActorRef[Checkout.Command]
) {

  import Payment._

  def start: Behavior[Payment.Command] =
    Behaviors.receiveMessage {
      case DoPayment =>
        orderManager ! PaymentReceived
        checkout ! Checkout.ConfirmPaymentReceived
        Behaviors.stopped

      case _ =>
        Behaviors.same
    }
}
