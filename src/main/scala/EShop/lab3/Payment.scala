package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object PaymentOld {

  sealed trait Command
  case object DoPayment extends Command

  sealed trait Event
  case object PaymentReceived extends Event
}

class PaymentOld(
  method: String,
  orderManager: ActorRef[Any],
  checkout: ActorRef[TypedCheckout.Command]
) {

  import PaymentOld._

  def start: Behavior[PaymentOld.Command] =
    Behaviors.receiveMessage {
      case DoPayment =>
        orderManager ! PaymentReceived
        checkout ! TypedCheckout.ConfirmPaymentReceived
        Behaviors.stopped

      case _ =>
        Behaviors.same
    }
}
