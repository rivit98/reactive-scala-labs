package EShop.lab3

import EShop.lab2.TypedCheckout
import EShop.lab3.Payment.Event
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
  checkout: ActorRef[TypedCheckout.Command]
) {

  import Payment._

  def start: Behavior[Payment.Command] =
    Behaviors.receiveMessage {
      case DoPayment =>
        orderManager ! PaymentReceived
        checkout ! TypedCheckout.ConfirmPaymentReceived
        Behaviors.stopped

      case _ =>
        Behaviors.same
    }
}
